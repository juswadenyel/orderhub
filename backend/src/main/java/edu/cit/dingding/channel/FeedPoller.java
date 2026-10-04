package edu.cit.dingding.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import edu.cit.dingding.shop.OrderAlreadyCancelledException;
import edu.cit.dingding.shop.OrderNotFoundException;
import edu.cit.dingding.shop.OrderService;
import edu.cit.dingding.shop.dto.OrderLineItemDto;
import edu.cit.dingding.shop.dto.OrderResponseDto;

/**
 * The core of the whole lab. Polls the feed, decides each new order
 * through the real Order module (never talking to Tiangge-specific
 * concepts from inside shop/inventory), confirms cancellations, and
 * persists its cursor + a dedup record for every eventId so a restart
 * picks up exactly where it left off.
 *
 * Only imports from shop: OrderService and its public DTOs/exceptions —
 * the same public surface OrderController already uses. Never imports
 * anything from inventory directly (Order already enforces stock rules).
 */
@Component
class FeedPoller {

    private static final int FEED_LIMIT = 50;

    private final TiangeeClient client;
    private final OrderService orderService;
    private final ChannelListingMapping listingMapping;
    private final ChannelEventRepository eventRepository;
    private final ChannelOrderMappingRepository orderMappingRepository;
    private final ChannelCursorRepository cursorRepository;
    private final ChannelFeedLock feedLock;

    FeedPoller(TiangeeClient client, OrderService orderService, ChannelListingMapping listingMapping,
               ChannelEventRepository eventRepository, ChannelOrderMappingRepository orderMappingRepository,
               ChannelCursorRepository cursorRepository, ChannelFeedLock feedLock) {
        this.client = client;
        this.orderService = orderService;
        this.listingMapping = listingMapping;
        this.eventRepository = eventRepository;
        this.orderMappingRepository = orderMappingRepository;
        this.cursorRepository = cursorRepository;
        this.feedLock = feedLock;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    synchronized void pollFeed() {
        feedLock.runIfAcquired(this::pollFeedWhileLocked);
    }

    private void pollFeedWhileLocked() {
        long cursor = getCursor();
        TiangeeClient.FeedResponse feed;
        try {
            feed = client.getFeed(cursor, FEED_LIMIT);
        } catch (RuntimeException e) {
            System.err.println("[channel] Feed poll failed, will retry next tick: " + e.getMessage());
            return;
        }

        long advanceTo = cursor;
        for (TiangeeClient.FeedEvent event : feed.events()) {
            if (eventRepository.existsById(event.eventId())) {
                advanceTo = event.seq();
                continue;
            }

            boolean handled;
            try {
                handled = switch (event.type()) {
                    case "ORDER_PLACED" -> handleOrderPlaced(event);
                    case "ORDER_CANCELLED" -> handleOrderCancelled(event);
                    default -> {
                        System.err.println("[channel] Unknown feed event type '" + event.type() + "', skipping");
                        yield true;
                    }
                };
            } catch (RuntimeException e) {
                System.err.println("[channel] Failed to process event " + event.eventId() + ": " + e.getMessage());
                handled = false;
            }

            if (handled) {
                eventRepository.save(new ChannelEventRecord(event.eventId()));
                advanceTo = event.seq();
            } else {
                // Stop here — don't advance the cursor past a failed event,
                // so the next tick re-fetches and retries it (and anything
                // after it) instead of silently skipping it.
                break;
            }
        }

        if (advanceTo != cursor) {
            setCursor(advanceTo);
        }
    }

    private boolean handleOrderPlaced(TiangeeClient.FeedEvent event) {
        // Idempotent at the order level too (defense in depth beyond the
        // eventId dedup table): if we already created an order for this
        // Tiangge orderId, don't create a second one — just (re)send the
        // decision we already made.
        ChannelOrderMapping mapping = orderMappingRepository.findByTiangeOrderId(event.orderId())
                .orElseGet(() -> createOrderForFeedEvent(event));

        if (mapping == null) {
            return true; // nothing mappable in this order — logged already, don't block the feed on it
        }

        try {
            client.decide(mapping.getTiangeOrderId(), mapping.getLastDecision(),
                    "SO-" + mapping.getOurOrderId(), null);
            return true;
        } catch (RuntimeException e) {
            System.err.println("[channel] Could not report decision for " + event.orderId() + ": " + e.getMessage());
            return false;
        }
    }

    private ChannelOrderMapping createOrderForFeedEvent(TiangeeClient.FeedEvent event) {
        List<OrderLineItemDto> items = new ArrayList<>();
        for (TiangeeClient.FeedLine line : event.lines()) {
            try {
                String productId = listingMapping.bySellerSku(line.sellerSku()).productId();
                OrderLineItemDto dto = new OrderLineItemDto();
                dto.setProductId(productId);
                dto.setQuantity(line.qty());
                items.add(dto);
            } catch (IllegalStateException unknownSku) {
                System.err.println("[channel] " + unknownSku.getMessage() + " (order " + event.orderId() + ")");
            }
        }

        if (items.isEmpty()) {
            System.err.println("[channel] Order " + event.orderId() + " had no mappable line items, skipping");
            return null;
        }

        OrderResponseDto result = orderService.placeOrder(items, true);
        String decision = switch (result.getStatus()) {
            case "CONFIRMED" -> "ACCEPTED";
            case "BACKORDERED" -> "BACKORDERED";
            default -> "REJECTED";
        };

        return orderMappingRepository.save(
                new ChannelOrderMapping(event.orderId(), result.getOrderId(), decision));
    }

    private boolean handleOrderCancelled(TiangeeClient.FeedEvent event) {
        Optional<ChannelOrderMapping> mapping = orderMappingRepository.findByTiangeOrderId(event.orderId());
        if (mapping.isEmpty()) {
            System.err.println("[channel] Cancellation for unmapped order " + event.orderId() + ", skipping");
            return true;
        }

        boolean restocked;
        try {
            restocked = orderService.cancelOrder(mapping.get().getOurOrderId());
        } catch (OrderAlreadyCancelledException alreadyDone) {
            // Fine — already cancelled locally (e.g. a previous attempt got
            // this far but failed to confirm to Tiangge). The mapping still
            // records whether the original decision reserved stock.
            restocked = "ACCEPTED".equals(mapping.get().getLastDecision());
        } catch (OrderNotFoundException notFound) {
            System.err.println("[channel] " + notFound.getMessage());
            return true;
        }

        try {
            client.confirmCancellation(event.orderId(), restocked);
            return true;
        } catch (RuntimeException e) {
            System.err.println("[channel] Could not confirm cancellation for " + event.orderId() + ": " + e.getMessage());
            return false;
        }
    }

    private long getCursor() {
        return cursorRepository.findById(1).map(ChannelCursor::getLastSeq).orElse(0L);
    }

    private void setCursor(long seq) {
        ChannelCursor cursor = cursorRepository.findById(1).orElse(new ChannelCursor(0));
        cursor.setLastSeq(seq);
        cursorRepository.save(cursor);
    }
}

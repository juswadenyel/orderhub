package edu.cit.dingding.shop;

import edu.cit.dingding.inventory.InventoryItem;
import edu.cit.dingding.inventory.InventoryItemNotFoundException;
import edu.cit.dingding.inventory.InventoryService;
import edu.cit.dingding.shop.dto.InventorySnapshotDto;
import edu.cit.dingding.shop.dto.OrderItemResultDto;
import edu.cit.dingding.shop.dto.OrderLineItemDto;
import edu.cit.dingding.shop.dto.OrderLineSummaryDto;
import edu.cit.dingding.shop.dto.OrderResponseDto;
import edu.cit.dingding.shop.dto.OrderSummaryDto;
import edu.cit.dingding.shop.events.OrderPlacedEvent;
import edu.cit.dingding.shop.events.OrderRejectedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Still depends only on the InventoryService INTERFACE — never
 * InventoryServiceImpl or InventoryRepository. Notification is never
 * imported here either; OrderService only publishes plain event objects
 * and has no idea anything is listening.
 */
@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(InventoryService inventoryService,
                         OrderRepository orderRepository,
                         OrderItemRepository orderItemRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.eventPublisher = eventPublisher;
    }

    /** Internal record of how one requested line item checked out before we reserve anything. */
    private record LineCheck(String productId, int quantity, boolean valid, String failureCode, String failureMessage) {
    }

    @Transactional
    public OrderResponseDto placeOrder(List<OrderLineItemDto> lineItems) {
        // Step 1: validate EVERY line against current stock before touching
        // anything. This is what makes the order all-or-nothing — if any
        // single item would fail, we never call reserve() for ANY item.
        List<LineCheck> checks = new ArrayList<>();
        boolean allValid = true;

        for (OrderLineItemDto line : lineItems) {
            try {
                InventoryItem item = inventoryService.getItem(line.getProductId());
                if (line.getQuantity() > item.getStock()) {
                    allValid = false;
                    String msg = "Requested quantity (" + line.getQuantity()
                            + ") exceeds available stock (" + item.getStock() + ") for " + item.getProductId();
                    checks.add(new LineCheck(line.getProductId(), line.getQuantity(), false, "INSUFFICIENT_STOCK", msg));
                } else {
                    checks.add(new LineCheck(line.getProductId(), line.getQuantity(), true, null, null));
                }
            } catch (InventoryItemNotFoundException ex) {
                allValid = false;
                checks.add(new LineCheck(line.getProductId(), line.getQuantity(), false, "PRODUCT_NOT_FOUND", ex.getMessage()));
            }
        }

        OrderStatus status = allValid ? OrderStatus.CONFIRMED : OrderStatus.REJECTED;
        String reason = allValid ? null : buildRejectionReason(checks);

        // Step 2: save the order header + every requested line item, win or
        // lose, so there's a full audit trail of what was asked for.
        Order order = orderRepository.save(new Order(status, reason));
        for (LineCheck check : checks) {
            orderItemRepository.save(new OrderItem(order.getOrderId(), check.productId(), check.quantity()));
        }

        // Step 3: only now, if everything validated, actually reserve stock.
        List<OrderItemResultDto> itemResults = new ArrayList<>();
        if (allValid) {
            for (LineCheck check : checks) {
                inventoryService.reserve(check.productId(), check.quantity()); // safe: already validated above
                itemResults.add(new OrderItemResultDto(check.productId(), "RESERVED"));
            }
        } else {
            for (LineCheck check : checks) {
                itemResults.add(new OrderItemResultDto(check.productId(), check.valid() ? "NOT_RESERVED" : check.failureCode()));
            }
        }

        List<InventorySnapshotDto> inventorySnapshot = lineItems.stream()
                .map(li -> {
                    try {
                        return toSnapshot(inventoryService.getItem(li.getProductId()));
                    } catch (InventoryItemNotFoundException ex) {
                        return null;
                    }
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());

        if (allValid) {
            eventPublisher.publishEvent(new OrderPlacedEvent(order.getOrderId()));
        } else {
            eventPublisher.publishEvent(new OrderRejectedEvent(order.getOrderId(), reason));
        }

        return new OrderResponseDto(order.getOrderId(), status.name(), reason, itemResults, inventorySnapshot);
    }

    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderAlreadyCancelledException(orderId);
        }

        // Only a CONFIRMED order actually holds reserved stock — a REJECTED
        // order never reserved anything, so there's nothing to give back.
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
            for (OrderItem item : items) {
                inventoryService.restock(item.getProductId(), item.getQuantity());
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    public List<OrderSummaryDto> getAllOrders() {
        List<Order> orders = orderRepository.findAll();
        List<OrderSummaryDto> summaries = new ArrayList<>();
        for (Order order : orders) {
            List<OrderLineSummaryDto> lines = orderItemRepository.findByOrderId(order.getOrderId()).stream()
                    .map(oi -> new OrderLineSummaryDto(oi.getProductId(), oi.getQuantity()))
                    .collect(Collectors.toList());
            summaries.add(new OrderSummaryDto(
                    order.getOrderId(), order.getStatus().name(), order.getReason(), order.getCreatedAt(), lines));
        }
        return summaries;
    }

    private String buildRejectionReason(List<LineCheck> checks) {
        return checks.stream()
                .filter(c -> !c.valid())
                .map(LineCheck::failureMessage)
                .collect(Collectors.joining("; "));
    }

    private InventorySnapshotDto toSnapshot(InventoryItem item) {
        return new InventorySnapshotDto(item.getProductId(), item.getName(), item.getStock());
    }
}

package edu.cit.dingding.channel;

import edu.cit.dingding.shop.events.OrderBackorderResolvedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class BackorderResolutionNotifier {

    private final TiangeeClient client;
    private final ChannelOrderMappingRepository orderMappingRepository;

    BackorderResolutionNotifier(TiangeeClient client, ChannelOrderMappingRepository orderMappingRepository) {
        this.client = client;
        this.orderMappingRepository = orderMappingRepository;
    }

    @EventListener
    public void onBackorderResolved(OrderBackorderResolvedEvent event) {
        Optional<ChannelOrderMapping> mapping = orderMappingRepository.findByOurOrderId(event.getOrderId());
        if (mapping.isEmpty()) {
            return; // this order didn't come from Tiangge — nothing to report
        }

        String status = switch (event.getNewStatus()) {
            case CONFIRMED -> "ACCEPTED";
            case CANCELLED -> "CANCELLED";
            default -> null;
        };
        if (status == null) {
            return;
        }

        mapping.get().setLastDecision(status);
        orderMappingRepository.save(mapping.get());
        try {
            client.resolve(mapping.get().getTiangeOrderId(), status);
        } catch (RuntimeException e) {
            System.err.println("[channel] Could not report backorder resolution for "
                    + mapping.get().getTiangeOrderId() + ": " + e.getMessage());
        }
    }
}

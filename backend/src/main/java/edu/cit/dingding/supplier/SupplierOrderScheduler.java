package edu.cit.dingding.supplier;

import edu.cit.dingding.supplier.events.SupplierOrderDeliveredEvent;
import edu.cit.dingding.supplier.events.SupplierOrderUnavailableEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class SupplierOrderScheduler {

    private final SupplierOrderRepository orderRepository;
    private final ProductCatalogMapping catalogMapping;
    private final LegacySupplyClient client;
    private final SupplierGatewayImpl gateway;
    private final ApplicationEventPublisher eventPublisher;

    SupplierOrderScheduler(SupplierOrderRepository orderRepository, ProductCatalogMapping catalogMapping,
                            LegacySupplyClient client, SupplierGatewayImpl gateway,
                            ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.catalogMapping = catalogMapping;
        this.client = client;
        this.gateway = gateway;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelay = 20000, initialDelay = 20000)
    void retryPendingOrders() {
        for (SupplierOrder order : orderRepository.findByStatus(SupplierOrderStatus.PENDING)) {
            try {
                List<LegacySupplyClient.TrackedOrder> existing = client.findByBuyerRef(order.getBuyerRef());
                if (!existing.isEmpty()) {
                    LegacySupplyClient.TrackedOrder match = existing.get(0);
                    order.setPoNumber(match.poNumber());
                    order.setStatus(SupplierGatewayImpl.mapStatusCode(match.statusCode()));
                    order.touch();
                    orderRepository.save(order);
                    continue;
                }
            } catch (RuntimeException lookupFailed) {
                // We cannot prove the previous POST did not create a PO. Keep
                // this request pending and reconcile by BuyerRef next tick;
                // issuing another POST here can create a duplicate order.
                System.err.println("[supplier] Could not reconcile BuyerRef " + order.getBuyerRef()
                        + ": " + lookupFailed.getMessage() + "; deferring placement");
                continue;
            }
            ProductCatalogMapping.SupplierItem item = catalogMapping.lookup(order.getProductId());
            gateway.attemptPlacement(order, item.sku());
        }
    }

    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    void trackOpenOrders() {
        List<SupplierOrderStatus> openStatuses = List.of(
                SupplierOrderStatus.ACCEPTED, SupplierOrderStatus.PICKING, SupplierOrderStatus.SHIPPED);

        for (SupplierOrder order : orderRepository.findByStatusIn(openStatuses)) {
            if (order.getPoNumber() == null) continue;
            try {
                LegacySupplyClient.TrackedOrder tracked = client.getStatus(order.getPoNumber());
                SupplierOrderStatus newStatus = SupplierGatewayImpl.mapStatusCode(tracked.statusCode());
                if (newStatus != order.getStatus()) {
                    order.setStatus(newStatus);
                    order.touch();
                    orderRepository.save(order);
                    if (newStatus == SupplierOrderStatus.DELIVERED) {
                        eventPublisher.publishEvent(new SupplierOrderDeliveredEvent(
                                order.getProductId(), order.getUnits(), order.getPoNumber()));
                    } else if (newStatus == SupplierOrderStatus.CANCELLED
                            || newStatus == SupplierOrderStatus.FAILED) {
                        eventPublisher.publishEvent(new SupplierOrderUnavailableEvent(
                                order.getProductId(), order.getPoNumber()));
                    }
                }
            } catch (RuntimeException e) {
                System.err.println("[supplier] Failed to check status for " + order.getPoNumber() + ": " + e.getMessage());
            }
        }
    }
}

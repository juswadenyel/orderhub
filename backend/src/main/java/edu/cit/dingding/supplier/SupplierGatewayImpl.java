package edu.cit.dingding.supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class SupplierGatewayImpl implements SupplierGateway {

    private final SupplierOrderRepository orderRepository;
    private final ProductCatalogMapping catalogMapping;
    private final LegacySupplyClient client;

    SupplierGatewayImpl(SupplierOrderRepository orderRepository, ProductCatalogMapping catalogMapping,
                         LegacySupplyClient client) {
        this.orderRepository = orderRepository;
        this.catalogMapping = catalogMapping;
        this.client = client;
    }

    @Override
    @Transactional
    public synchronized SupplierOrderResult reorder(String productId, int unitsNeeded) {
        List<SupplierOrderStatus> openStatuses = List.of(
                SupplierOrderStatus.PENDING, SupplierOrderStatus.ACCEPTED,
                SupplierOrderStatus.PICKING, SupplierOrderStatus.SHIPPED);
        List<SupplierOrder> existing = orderRepository.findByProductIdAndStatusIn(productId, openStatuses);
        if (!existing.isEmpty()) {
            return toResult(existing.get(0));
        }

        ProductCatalogMapping.SupplierItem item = catalogMapping.lookup(productId);
        int cases = ceilDiv(unitsNeeded, item.packSize());

        String requestId = java.util.UUID.randomUUID().toString();
        SupplierOrder order = orderRepository.save(new SupplierOrder(productId, requestId, cases, unitsNeeded));
        order.setBuyerRef("RO-" + order.getId());
        order = orderRepository.save(order);

        return attemptPlacement(order, item.sku());
    }

    @Override
    public SupplierOrderResult checkStatus(Long supplierOrderId) {
        SupplierOrder order = orderRepository.findById(supplierOrderId)
                .orElseThrow(() -> new IllegalArgumentException("No supplier order " + supplierOrderId));
        if (order.getPoNumber() == null) {
            return toResult(order);
        }
        LegacySupplyClient.TrackedOrder tracked = client.getStatus(order.getPoNumber());
        SupplierOrderStatus newStatus = mapStatusCode(tracked.statusCode());
        if (newStatus != order.getStatus()) {
            order.setStatus(newStatus);
            order.touch();
            order = orderRepository.save(order);
        }
        return toResult(order);
    }

    @Override
    public boolean hasOpenPurchaseOrder(String productId) {
        List<SupplierOrderStatus> openStatuses = List.of(
                SupplierOrderStatus.PENDING, SupplierOrderStatus.ACCEPTED,
                SupplierOrderStatus.PICKING, SupplierOrderStatus.SHIPPED);
        return !orderRepository.findByProductIdAndStatusIn(productId, openStatuses).isEmpty();
    }

    SupplierOrderResult attemptPlacement(SupplierOrder order, String sku) {
        try {
            LegacySupplyClient.PlacedOrder placed = client.placeOrder(
                    sku, order.getCases(), order.getBuyerRef(), order.getRequestId());
            order.setPoNumber(placed.poNumber());
            order.setStatus(mapStatusCode(placed.statusCode()));
        } catch (SupplierPermanentException e) {
            order.setStatus(SupplierOrderStatus.FAILED);
        } catch (SupplierTransientException e) {
            // Leave PENDING — SupplierOrderScheduler.retryPendingOrders() retries later.
        }
        order.touch();
        order = orderRepository.save(order);
        return toResult(order);
    }

    static SupplierOrderStatus mapStatusCode(int code) {
        return switch (code) {
            case 10 -> SupplierOrderStatus.ACCEPTED;
            case 20 -> SupplierOrderStatus.PICKING;
            case 30 -> SupplierOrderStatus.SHIPPED;
            case 40 -> SupplierOrderStatus.DELIVERED;
            // LegacySupply uses 90 for a terminal cancelled/declined PO.
            // Treating it as ACCEPTED would leave phantom supply reserved.
            case 90 -> SupplierOrderStatus.CANCELLED;
            default -> {
                System.err.println("[supplier] Unrecognized LegacySupply StatusCode: " + code);
                yield SupplierOrderStatus.ACCEPTED;
            }
        };
    }

    private static int ceilDiv(int units, int packSize) {
        return (units + packSize - 1) / packSize;
    }

    private SupplierOrderResult toResult(SupplierOrder order) {
        return new SupplierOrderResult(
                order.getId(), order.getProductId(), order.getBuyerRef(), order.getPoNumber(), order.getStatus());
    }
}

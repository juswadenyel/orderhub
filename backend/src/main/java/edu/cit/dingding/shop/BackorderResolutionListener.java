package edu.cit.dingding.shop;

import edu.cit.dingding.inventory.InventoryItem;
import edu.cit.dingding.inventory.InventoryService;
import edu.cit.dingding.shop.events.OrderBackorderResolvedEvent;
import edu.cit.dingding.supplier.SupplierGateway;
import edu.cit.dingding.supplier.events.SupplierOrderDeliveredEvent;
import edu.cit.dingding.supplier.events.SupplierOrderUnavailableEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Only imports SupplierOrderDeliveredEvent (a plain event class) from the
 * supplier module, plus the SupplierGateway interface it already used for
 * the backorder decision itself — never anything LegacySupply-shaped.
 *
 * Every time a supplier delivery lands, re-checks every BACKORDERED order
 * touching that product: if the WHOLE order (all its lines, not just the
 * delivered one) can now be reserved, confirm it. If it still can't be
 * filled and nothing is left on order for any of its short items, give up
 * and cancel it rather than backorder it forever.
 */
@Component
class BackorderResolutionListener {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final ApplicationEventPublisher eventPublisher;

    BackorderResolutionListener(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                                 InventoryService inventoryService, SupplierGateway supplierGateway,
                                 ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    @Transactional
    public void onSupplierOrderDelivered(SupplierOrderDeliveredEvent event) {
        resolveForProduct(event.getProductId());
    }

    @EventListener
    @Transactional
    public void onSupplierOrderUnavailable(SupplierOrderUnavailableEvent event) {
        resolveForProduct(event.getProductId());
    }

    private void resolveForProduct(String productId) {
        List<Order> backorderedOrders = orderRepository.findByStatus(OrderStatus.BACKORDERED);
        List<List<OrderItem>> itemsByOrder = backorderedOrders.stream()
            .map(order -> orderItemRepository.findByOrderId(order.getOrderId()))
            .toList();
        Set<String> productIds = new TreeSet<>();
        for (List<OrderItem> items : itemsByOrder) {
            for (OrderItem item : items) {
                productIds.add(item.getProductId());
            }
        }
        for (String id : productIds) {
            inventoryService.lockItem(id);
        }

        for (int index = 0; index < backorderedOrders.size(); index++) {
            Order order = backorderedOrders.get(index);
            List<OrderItem> items = itemsByOrder.get(index);

            boolean touchesDeliveredProduct = items.stream()
                    .anyMatch(i -> i.getProductId().equals(productId));
            if (!touchesDeliveredProduct) {
                continue;
            }

            boolean allAvailableNow = items.stream().allMatch(i -> {
                InventoryItem current = inventoryService.getItem(i.getProductId());
                return i.getQuantity() <= current.getStock();
            });

            if (allAvailableNow) {
                for (OrderItem item : items) {
                    if (!inventoryService.reserve(item.getProductId(), item.getQuantity())) {
                        throw new IllegalStateException("Stock changed while resolving backorder " + order.getOrderId());
                    }
                }
                order.setStatus(OrderStatus.CONFIRMED);
                orderRepository.save(order);
                eventPublisher.publishEvent(new OrderBackorderResolvedEvent(order.getOrderId(), OrderStatus.CONFIRMED));
                continue;
            }

            boolean anyStillComing = items.stream().anyMatch(i -> {
                InventoryItem current = inventoryService.getItem(i.getProductId());
                return i.getQuantity() > current.getStock() && supplierGateway.hasOpenPurchaseOrder(i.getProductId());
            });

            if (!anyStillComing) {
                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);
                eventPublisher.publishEvent(new OrderBackorderResolvedEvent(order.getOrderId(), OrderStatus.CANCELLED));
            }
            // else: still genuinely waiting on another delivery — leave BACKORDERED.
        }
    }
}

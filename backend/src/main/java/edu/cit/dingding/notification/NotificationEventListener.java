package edu.cit.dingding.notification;

import edu.cit.dingding.inventory.events.LowStockEvent;
import edu.cit.dingding.shop.events.OrderPlacedEvent;
import edu.cit.dingding.shop.events.OrderRejectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The ONLY things this class imports from outside the notification package
 * are three plain event classes (edu.cit.dingding.shop.events.* and
 * edu.cit.dingding.inventory.events.*). It never imports OrderService,
 * InventoryService, or either of their impls — it doesn't even know they
 * exist. Spring's ApplicationEventPublisher is what connects them, not a
 * direct method call.
 *
 * These listeners run synchronously (no @Async) — see the README for why:
 * short of putting this on a separate thread/queue, this keeps ordering
 * simple and guarantees the notification is written in the same request,
 * which matters for the "capture Network tab + notification feed" test
 * cases this lab asks for.
 */
@Component
class NotificationEventListener {

    private final NotificationService notificationService;

    NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        notificationService.log("Order " + event.getOrderId() + " confirmed");
    }

    @EventListener
    public void onOrderRejected(OrderRejectedEvent event) {
        notificationService.log("Order " + event.getOrderId() + " rejected: " + event.getReason());
    }

    @EventListener
    public void onLowStock(LowStockEvent event) {
        notificationService.log("Reorder needed: " + event.getProductName() + " (" + event.getProductId()
                + ") is down to " + event.getRemainingStock() + " units");
    }
}

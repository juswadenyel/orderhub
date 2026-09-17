package edu.cit.dingding.shop.events;

/**
 * Published by OrderService when an order is CONFIRMED. This class (and its
 * sibling OrderRejectedEvent) is the ONLY thing the notification module is
 * allowed to depend on from the shop side — it lives in its own `events`
 * sub-package specifically so that dependency is on a plain data class, not
 * on OrderService itself.
 */
public class OrderPlacedEvent {

    private final Long orderId;

    public OrderPlacedEvent(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderId() {
        return orderId;
    }
}

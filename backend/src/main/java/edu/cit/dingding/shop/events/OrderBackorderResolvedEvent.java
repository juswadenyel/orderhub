package edu.cit.dingding.shop.events;

import edu.cit.dingding.shop.OrderStatus;

/**
 * Published when a BACKORDERED order is finally settled — either it could
 * be filled (newStatus = CONFIRMED) or it never will be (newStatus =
 * CANCELLED). Channel listens for this to tell Tiangge; Order has no idea
 * who's listening or why.
 */
public class OrderBackorderResolvedEvent {

    private final Long orderId;
    private final OrderStatus newStatus;

    public OrderBackorderResolvedEvent(Long orderId, OrderStatus newStatus) {
        this.orderId = orderId;
        this.newStatus = newStatus;
    }

    public Long getOrderId() {
        return orderId;
    }

    public OrderStatus getNewStatus() {
        return newStatus;
    }
}

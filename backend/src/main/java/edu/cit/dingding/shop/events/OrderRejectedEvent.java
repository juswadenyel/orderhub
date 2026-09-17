package edu.cit.dingding.shop.events;

public class OrderRejectedEvent {

    private final Long orderId;
    private final String reason;

    public OrderRejectedEvent(Long orderId, String reason) {
        this.orderId = orderId;
        this.reason = reason;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getReason() {
        return reason;
    }
}

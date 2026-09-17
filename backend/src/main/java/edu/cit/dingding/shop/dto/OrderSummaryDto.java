package edu.cit.dingding.shop.dto;

import java.time.Instant;
import java.util.List;

/** One row of GET /api/orders — the order history view on the frontend. */
public class OrderSummaryDto {

    private final Long orderId;
    private final String status;
    private final String reason;
    private final Instant createdAt;
    private final List<OrderLineSummaryDto> items;

    public OrderSummaryDto(Long orderId, String status, String reason, Instant createdAt,
                            List<OrderLineSummaryDto> items) {
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
        this.createdAt = createdAt;
        this.items = items;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<OrderLineSummaryDto> getItems() {
        return items;
    }
}

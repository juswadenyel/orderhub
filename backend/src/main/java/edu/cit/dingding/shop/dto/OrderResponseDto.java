package edu.cit.dingding.shop.dto;

import java.util.List;

public class OrderResponseDto {

    private final Long orderId;
    private final String status;
    private final String reason;
    private final List<OrderItemResultDto> items;
    private final List<InventorySnapshotDto> inventory;

    public OrderResponseDto(Long orderId, String status, String reason,
                             List<OrderItemResultDto> items, List<InventorySnapshotDto> inventory) {
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
        this.items = items;
        this.inventory = inventory;
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

    public List<OrderItemResultDto> getItems() {
        return items;
    }

    public List<InventorySnapshotDto> getInventory() {
        return inventory;
    }
}

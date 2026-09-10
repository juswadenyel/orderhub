package edu.cit.dingding.shop.dto;

public class OrderResponseDto {

    private final String status;
    private final String reason;
    private final InventorySnapshotDto inventory;

    public OrderResponseDto(String status, String reason, InventorySnapshotDto inventory) {
        this.status = status;
        this.reason = reason;
        this.inventory = inventory;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public InventorySnapshotDto getInventory() {
        return inventory;
    }
}

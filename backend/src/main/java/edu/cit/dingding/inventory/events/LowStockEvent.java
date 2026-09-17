package edu.cit.dingding.inventory.events;

/**
 * Published by InventoryServiceImpl itself, right after a reserve() leaves a
 * product below the low-stock threshold. Living in inventory.events (not
 * inventory itself) means the notification module can depend on this one
 * class without depending on InventoryService or InventoryServiceImpl.
 */
public class LowStockEvent {

    private final String productId;
    private final String productName;
    private final int remainingStock;

    public LowStockEvent(String productId, String productName, int remainingStock) {
        this.productId = productId;
        this.productName = productName;
        this.remainingStock = remainingStock;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getRemainingStock() {
        return remainingStock;
    }
}

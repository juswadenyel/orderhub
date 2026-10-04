package edu.cit.dingding.inventory.events;

/**
 * Published after EVERY successful stock mutation, for ANY reason —
 * reserve() (an order, from any channel) or restock() (a cancellation or a
 * supplier delivery). This one event covers Lab 4 Task 3 ("any change in
 * your Inventory... publishes the new available quantity") automatically,
 * since every stock-changing path in InventoryServiceImpl already funnels
 * through these two methods — Inventory never needed to know a channel
 * module exists to make this true.
 */
public class InventoryStockChangedEvent {

    private final String productId;
    private final int newStock;

    public InventoryStockChangedEvent(String productId, int newStock) {
        this.productId = productId;
        this.newStock = newStock;
    }

    public String getProductId() {
        return productId;
    }

    public int getNewStock() {
        return newStock;
    }
}

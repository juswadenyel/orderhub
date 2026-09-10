package edu.cit.dingding.inventory;

/**
 * The ONLY thing the rest of the app (specifically the shop/Order module) is
 * allowed to depend on. It never sees InventoryItem's JPA internals or the
 * repository — just these two operations.
 */
public interface InventoryService {

    /**
     * Look up a product's current stock info.
     *
     * @throws InventoryItemNotFoundException if productId doesn't exist
     */
    InventoryItem getItem(String productId);

    /**
     * Attempt to take `quantity` units out of stock for productId.
     *
     * @return true if the reservation succeeded (stock was sufficient and has
     *         now been decremented), false if there wasn't enough stock.
     * @throws InventoryItemNotFoundException if productId doesn't exist
     */
    boolean reserve(String productId, int quantity);

    /**
     * Not part of the original spec's two methods, but the React dropdown
     * needs something to populate itself with — this is the smallest useful
     * read operation to add for that, still going through the interface.
     */
    java.util.List<InventoryItem> getAllItems();
}

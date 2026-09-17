package edu.cit.dingding.inventory;

import java.util.List;

public interface InventoryService {

    InventoryItem getItem(String productId);

    boolean reserve(String productId, int quantity);

    /**
     * New in Lab 2 — returns quantity to stock (used by order cancellation).
     * Same rule as reserve(): the implementation stays package-private.
     */
    void restock(String productId, int quantity);

    List<InventoryItem> getAllItems();
}

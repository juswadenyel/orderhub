package edu.cit.dingding.inventory;

/**
 * Public: the shop module needs to be able to catch this, even though it
 * can never see InventoryServiceImpl or InventoryRepository.
 */
public class InventoryItemNotFoundException extends RuntimeException {
    public InventoryItemNotFoundException(String productId) {
        super("No inventory item found for productId '" + productId + "'");
    }
}

package edu.cit.dingding.supplier;

/**
 * The only door into this module. Order and Inventory (and now Channel)
 * may depend on this interface and its plain result/enum types — nothing
 * LegacySupply-shaped ever crosses this line.
 */
public interface SupplierGateway {

    SupplierOrderResult reorder(String productId, int unitsNeeded);

    SupplierOrderResult checkStatus(Long supplierOrderId);

    /**
     * Lab 4: does this product already have an open (not yet DELIVERED,
     * not FAILED) purchase order in flight? Order uses this to decide
     * REJECTED vs BACKORDERED for a Tiangge order it can't fill right now
     * — "backorder only when every item you're short of already has an
     * open LegacySupply purchase order."
     */
    boolean hasOpenPurchaseOrder(String productId);
}

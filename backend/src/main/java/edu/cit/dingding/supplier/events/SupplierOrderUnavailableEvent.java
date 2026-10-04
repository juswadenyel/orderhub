package edu.cit.dingding.supplier.events;

/** A supplier purchase order reached a terminal state without delivery. */
public class SupplierOrderUnavailableEvent {
    private final String productId;
    private final String poNumber;

    public SupplierOrderUnavailableEvent(String productId, String poNumber) {
        this.productId = productId;
        this.poNumber = poNumber;
    }

    public String getProductId() { return productId; }
    public String getPoNumber() { return poNumber; }
}

package edu.cit.dingding.supplier.events;

public class SupplierOrderDeliveredEvent {
    private final String productId;
    private final int unitsDelivered;
    private final String poNumber;

    public SupplierOrderDeliveredEvent(String productId, int unitsDelivered, String poNumber) {
        this.productId = productId;
        this.unitsDelivered = unitsDelivered;
        this.poNumber = poNumber;
    }

    public String getProductId() { return productId; }
    public int getUnitsDelivered() { return unitsDelivered; }
    public String getPoNumber() { return poNumber; }
}

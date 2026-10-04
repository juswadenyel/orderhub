package edu.cit.dingding.supplier;

public class SupplierOrderResult {
    private final Long supplierOrderId;
    private final String productId;
    private final String buyerRef;
    private final String poNumber;
    private final SupplierOrderStatus status;

    public SupplierOrderResult(Long supplierOrderId, String productId, String buyerRef,
                                String poNumber, SupplierOrderStatus status) {
        this.supplierOrderId = supplierOrderId;
        this.productId = productId;
        this.buyerRef = buyerRef;
        this.poNumber = poNumber;
        this.status = status;
    }

    public Long getSupplierOrderId() { return supplierOrderId; }
    public String getProductId() { return productId; }
    public String getBuyerRef() { return buyerRef; }
    public String getPoNumber() { return poNumber; }
    public SupplierOrderStatus getStatus() { return status; }
}

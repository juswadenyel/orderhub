package edu.cit.dingding.supplier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "supplier_orders")
class SupplierOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "product_id")
    private String productId;

    @Column(name = "buyer_ref")
    private String buyerRef;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "po_number")
    private String poNumber;

    @Column(name = "cases")
    private int cases;

    @Column(name = "units")
    private int units;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private SupplierOrderStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected SupplierOrder() {
    }

    SupplierOrder(String productId, String requestId, int cases, int units) {
        this.productId = productId;
        this.requestId = requestId;
        this.cases = cases;
        this.units = units;
        this.status = SupplierOrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    Long getId() { return id; }
    String getProductId() { return productId; }
    String getBuyerRef() { return buyerRef; }
    void setBuyerRef(String buyerRef) { this.buyerRef = buyerRef; }
    String getRequestId() { return requestId; }
    String getPoNumber() { return poNumber; }
    void setPoNumber(String poNumber) { this.poNumber = poNumber; }
    int getCases() { return cases; }
    int getUnits() { return units; }
    SupplierOrderStatus getStatus() { return status; }
    void setStatus(SupplierOrderStatus status) { this.status = status; }
    void touch() { this.updatedAt = Instant.now(); }
}

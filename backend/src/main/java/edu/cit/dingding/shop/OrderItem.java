package edu.cit.dingding.shop;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One line of an order (one product + quantity). Deliberately NOT a JPA
 * @ManyToOne relationship back to Order — OrderService just stitches them
 * together by orderId, which keeps both entities simple for a lab this size.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "product_id")
    private String productId;

    @Column(name = "quantity")
    private int quantity;

    protected OrderItem() {
        // required by JPA
    }

    public OrderItem(Long orderId, String productId, int quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }
}

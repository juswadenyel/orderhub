package edu.cit.dingding.shop.dto;

public class OrderItemResultDto {

    private final String productId;
    private final String outcome; // RESERVED | INSUFFICIENT_STOCK | PRODUCT_NOT_FOUND | NOT_RESERVED

    public OrderItemResultDto(String productId, String outcome) {
        this.productId = productId;
        this.outcome = outcome;
    }

    public String getProductId() {
        return productId;
    }

    public String getOutcome() {
        return outcome;
    }
}

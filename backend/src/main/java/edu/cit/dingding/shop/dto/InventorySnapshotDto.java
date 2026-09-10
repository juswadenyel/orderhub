package edu.cit.dingding.shop.dto;

public class InventorySnapshotDto {

    private final String productId;
    private final String name;
    private final int stock;

    public InventorySnapshotDto(String productId, String name, int stock) {
        this.productId = productId;
        this.name = name;
        this.stock = stock;
    }

    public String getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public int getStock() {
        return stock;
    }
}

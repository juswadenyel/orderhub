package edu.cit.dingding.supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
class ProductCatalogMapping {

    record SupplierItem(String sku, int packSize) {
    }

    private final Map<String, SupplierItem> byProductId = new HashMap<>();

    ProductCatalogMapping(
            @Value("${legacysupply.mapping.P100:}") String p100,
            @Value("${legacysupply.mapping.P200:}") String p200,
            @Value("${legacysupply.mapping.P300:}") String p300) {
        put("P100", p100);
        put("P200", p200);
        put("P300", p300);
    }

    private void put(String productId, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return;
        }
        String[] parts = rawValue.split(":");
        if (parts.length != 2) {
            throw new IllegalStateException(
                    "legacysupply.mapping." + productId + " must look like 'SKU:packSize', got: " + rawValue);
        }
        byProductId.put(productId, new SupplierItem(parts[0].trim(), Integer.parseInt(parts[1].trim())));
    }

    SupplierItem lookup(String productId) {
        SupplierItem item = byProductId.get(productId);
        if (item == null) {
            throw new IllegalStateException(
                    "No LegacySupply catalog mapping configured for product '" + productId
                            + "' — add legacysupply.mapping." + productId + "=SKU:packSize to application-local.properties");
        }
        return item;
    }

    String supplierSkuFor(String productId) {
        return lookup(productId).sku();
    }
}

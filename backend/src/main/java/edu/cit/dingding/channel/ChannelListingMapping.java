package edu.cit.dingding.channel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Our product_id <-> Tiangge sellerSku/title, plus the LegacySupply
 * SupplierSku each listing declares. Independent from the supplier
 * module's own ProductCatalogMapping on purpose — channel needing to know
 * a SupplierSku to publish a listing is a legitimate, expected part of its
 * job (the Tiangge manual requires it), not a boundary violation, but
 * there's no reason for channel to reach into supplier's internals to get
 * it. A little config duplication here is the trade for zero coupling.
 */
@Component
class ChannelListingMapping {

    record Listing(String productId, String sellerSku, String title, String supplierSku) {
    }

    private final Map<String, Listing> byProductId = new HashMap<>();
    private final Map<String, Listing> bySellerSku = new HashMap<>();

    ChannelListingMapping(
            @Value("${tiangge.listing.P100:}") String p100,
            @Value("${tiangge.listing.P200:}") String p200,
            @Value("${tiangge.listing.P300:}") String p300) {
        put("P100", p100);
        put("P200", p200);
        put("P300", p300);
    }

    private void put(String productId, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return;
        }
        // Format: sellerSku:title:supplierSku
        String[] parts = rawValue.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalStateException(
                    "tiangge.listing." + productId + " must look like 'sellerSku:title:supplierSku', got: " + rawValue);
        }
        Listing listing = new Listing(productId, parts[0].trim(), parts[1].trim(), parts[2].trim());
        byProductId.put(productId, listing);
        bySellerSku.put(listing.sellerSku(), listing);
    }

    java.util.Collection<Listing> allListings() {
        return byProductId.values();
    }

    Listing bySellerSku(String sellerSku) {
        Listing listing = bySellerSku.get(sellerSku);
        if (listing == null) {
            throw new IllegalStateException("No Tiangge listing mapping for sellerSku '" + sellerSku + "'");
        }
        return listing;
    }

    Listing byProductId(String productId) {
        Listing listing = byProductId.get(productId);
        if (listing == null) {
            throw new IllegalStateException("No Tiangge listing mapping for product '" + productId + "'");
        }
        return listing;
    }

    boolean hasMapping(String productId) {
        return byProductId.containsKey(productId);
    }
}

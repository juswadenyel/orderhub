package edu.cit.dingding.channel;

import edu.cit.dingding.inventory.InventoryService;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

@Service
class TiangeeChannelService implements SalesChannelGateway {

    private final TiangeeClient client;
    private final ChannelListingMapping listingMapping;
    private final InventoryService inventoryService;

    TiangeeChannelService(TiangeeClient client, ChannelListingMapping listingMapping,
                           InventoryService inventoryService) {
        this.client = client;
        this.listingMapping = listingMapping;
        this.inventoryService = inventoryService;
    }

    @Override
    public void publishListings() {
        List<TiangeeClient.ListingDto> listings = listingMapping.allListings().stream()
                .map(l -> new TiangeeClient.ListingDto(l.sellerSku(), l.title(), l.supplierSku()))
                .toList();
        if (listings.isEmpty()) {
            System.err.println("[channel] No tiangge.listing.* mappings configured — nothing to publish");
            return;
        }
        client.publishListings(listings);
    }

    @Override
    public synchronized void publishStock(String productId, int available) {
        if (!listingMapping.hasMapping(productId)) {
            return; // not a product we sell on Tiangge — nothing to do
        }
        String sellerSku = listingMapping.byProductId(productId).sellerSku();
        client.publishStock(List.of(new TiangeeClient.StockDto(sellerSku, available)));
    }

    /** Used once at startup to publish current stock for every listed product, not just one. */
    synchronized void publishAllCurrentStock() {
        List<TiangeeClient.StockDto> stock = listingMapping.allListings().stream()
                .map(l -> new TiangeeClient.StockDto(l.sellerSku(), inventoryService.getItem(l.productId()).getStock()))
                .toList();
        if (!stock.isEmpty()) {
            client.publishStock(stock);
        }
    }

    /** Repairs a missed post-commit notification after a temporary Tiangge outage. */
    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    void reconcileStock() {
        try {
            publishAllCurrentStock();
        } catch (RuntimeException e) {
            System.err.println("[channel] Stock reconciliation failed; will retry: " + e.getMessage());
        }
    }
}

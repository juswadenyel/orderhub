package edu.cit.dingding.channel;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import edu.cit.dingding.inventory.InventoryService;
import edu.cit.dingding.inventory.events.InventoryStockChangedEvent;

@Component
class StockSyncListener {

    private final SalesChannelGateway channelGateway;
    private final InventoryService inventoryService;

    StockSyncListener(SalesChannelGateway channelGateway, InventoryService inventoryService) {
        this.channelGateway = channelGateway;
        this.inventoryService = inventoryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public synchronized void onStockChanged(InventoryStockChangedEvent event) {
        try {
            // A different transaction may commit while this callback waits for
            // the lock. Publish the committed value, not a stale event snapshot.
            int currentStock = inventoryService.getItem(event.getProductId()).getStock();
            System.out.println(
                "[channel] Stock changed: " +
                event.getProductId() +
                " -> " +
                currentStock
            );

            channelGateway.publishStock(
                event.getProductId(),
                currentStock
            );

            System.out.println(
                "[channel] Stock successfully published: " +
                event.getProductId() +
                " -> " +
                currentStock
            );

        } catch (RuntimeException e) {
            System.err.println(
                "[channel] FAILED to publish stock for " +
                event.getProductId() +
                " -> " +
                event.getNewStock() +
                ": " +
                e.getMessage()
            );
        }
    }
}

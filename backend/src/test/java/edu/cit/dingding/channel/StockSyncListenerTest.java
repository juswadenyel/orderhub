package edu.cit.dingding.channel;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.cit.dingding.inventory.InventoryItem;
import edu.cit.dingding.inventory.InventoryService;
import edu.cit.dingding.inventory.events.InventoryStockChangedEvent;

class StockSyncListenerTest {

    @Test
    void publishesCommittedStockInsteadOfStaleEventSnapshot() {
        SalesChannelGateway gateway = mock(SalesChannelGateway.class);
        InventoryService inventoryService = mock(InventoryService.class);
        when(inventoryService.getItem("P100")).thenReturn(new InventoryItem("P100", "Mouse", 8));

        StockSyncListener listener = new StockSyncListener(gateway, inventoryService);
        listener.onStockChanged(new InventoryStockChangedEvent("P100", 9));

        verify(gateway).publishStock("P100", 8);
    }
}
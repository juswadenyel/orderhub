package edu.cit.dingding.inventory;

import edu.cit.dingding.supplier.events.SupplierOrderDeliveredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Only imports the supplier module's event class — never SupplierGateway,
 * never anything LegacySupply-shaped. Inventory just knows how to restock,
 * which it already could.
 */
@Component
class InventoryReplenishmentListener {

    private final InventoryService inventoryService;

    InventoryReplenishmentListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    public void onSupplierOrderDelivered(SupplierOrderDeliveredEvent event) {
        inventoryService.restock(event.getProductId(), event.getUnitsDelivered());
    }
}

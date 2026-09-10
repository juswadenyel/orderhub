package edu.cit.dingding.inventory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only endpoint so the React product dropdown has something to fetch.
 * Not required by the lab spec, but the frontend needs it — still goes
 * through InventoryService, same as everything else outside this package.
 */
@RestController
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/api/inventory")
    public List<InventoryItem> listInventory() {
        return inventoryService.getAllItems();
    }
}

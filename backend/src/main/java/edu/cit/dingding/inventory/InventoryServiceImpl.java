package edu.cit.dingding.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private (no "public" here) — this is the module boundary.
 * Nothing outside edu.cit.dingding.inventory can name this class, construct
 * it, or cast down to it. The shop module can only ever see it through the
 * public InventoryService interface that Spring injects.
 */
@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public InventoryItem getItem(String productId) {
        return inventoryRepository.findById(productId)
                .orElseThrow(() -> new InventoryItemNotFoundException(productId));
    }

    @Override
    @Transactional
    public boolean reserve(String productId, int quantity) {
        InventoryItem item = getItem(productId);

        if (quantity > item.getStock()) {
            return false;
        }

        item.setStock(item.getStock() - quantity);
        inventoryRepository.save(item);
        return true;
    }

    @Override
    public java.util.List<InventoryItem> getAllItems() {
        return inventoryRepository.findAll();
    }
}

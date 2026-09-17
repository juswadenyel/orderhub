package edu.cit.dingding.inventory;

import edu.cit.dingding.inventory.events.LowStockEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Package-private (no "public") — this IS the module boundary.
@Service
class InventoryServiceImpl implements InventoryService {

    // Below this many units remaining, we consider a product low stock and
    // fire an event for the Notification module to log. A simple constant
    // is enough for this lab — a real system would likely make this
    // per-product and configurable via the database.
    private static final int LOW_STOCK_THRESHOLD = 5;

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    InventoryServiceImpl(InventoryRepository inventoryRepository, ApplicationEventPublisher eventPublisher) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
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

        if (item.getStock() < LOW_STOCK_THRESHOLD) {
            eventPublisher.publishEvent(new LowStockEvent(item.getProductId(), item.getName(), item.getStock()));
        }

        return true;
    }

    @Override
    @Transactional
    public void restock(String productId, int quantity) {
        InventoryItem item = getItem(productId);
        item.setStock(item.getStock() + quantity);
        inventoryRepository.save(item);
    }

    @Override
    public List<InventoryItem> getAllItems() {
        return inventoryRepository.findAll();
    }
}

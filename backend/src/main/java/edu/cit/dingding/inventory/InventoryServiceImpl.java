package edu.cit.dingding.inventory;

import edu.cit.dingding.inventory.events.InventoryStockChangedEvent;
import edu.cit.dingding.inventory.events.LowStockEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Package-private (no "public") — this IS the module boundary.
@Service
class InventoryServiceImpl implements InventoryService {

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
    public InventoryItem lockItem(String productId) {
        return inventoryRepository.findLockedByProductId(productId)
                .orElseThrow(() -> new InventoryItemNotFoundException(productId));
    }

    @Override
    @Transactional
    public boolean reserve(String productId, int quantity) {
        InventoryItem item = inventoryRepository.findLockedByProductId(productId)
            .orElseThrow(() -> new InventoryItemNotFoundException(productId));

        if (quantity > item.getStock()) {
            return false;
        }

        item.setStock(item.getStock() - quantity);
        inventoryRepository.save(item);

        eventPublisher.publishEvent(new InventoryStockChangedEvent(item.getProductId(), item.getStock()));

        if (item.getStock() < LOW_STOCK_THRESHOLD) {
            eventPublisher.publishEvent(new LowStockEvent(item.getProductId(), item.getName(), item.getStock()));
        }

        return true;
    }

    @Override
    @Transactional
    public void restock(String productId, int quantity) {
        InventoryItem item = inventoryRepository.findLockedByProductId(productId)
            .orElseThrow(() -> new InventoryItemNotFoundException(productId));
        item.setStock(item.getStock() + quantity);
        inventoryRepository.save(item);

        eventPublisher.publishEvent(new InventoryStockChangedEvent(item.getProductId(), item.getStock()));
    }

    @Override
    public List<InventoryItem> getAllItems() {
        return inventoryRepository.findAll();
    }
}

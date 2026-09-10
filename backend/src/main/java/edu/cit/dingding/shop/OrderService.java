package edu.cit.dingding.shop;

import edu.cit.dingding.inventory.InventoryItem;
import edu.cit.dingding.inventory.InventoryItemNotFoundException;
import edu.cit.dingding.inventory.InventoryService;
import edu.cit.dingding.shop.dto.InventorySnapshotDto;
import edu.cit.dingding.shop.dto.OrderResponseDto;
import org.springframework.stereotype.Service;

/**
 * This is the in-process integration point. OrderService depends only on the
 * InventoryService INTERFACE (constructor injection) — it never imports
 * InventoryServiceImpl or InventoryRepository, and couldn't even if it wanted
 * to, since both are package-private inside edu.cit.dingding.inventory.
 *
 * Calling inventoryService.reserve(...) below is a plain Java method call:
 * same JVM, same call stack, no network hop, no serialization.
 */
@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    public OrderResponseDto placeOrder(String productId, int quantity) {
        try {
            boolean reserved = inventoryService.reserve(productId, quantity);
            InventoryItem currentState = inventoryService.getItem(productId);

            if (reserved) {
                orderRepository.save(new Order(productId, quantity, OrderStatus.CONFIRMED, null));
                return new OrderResponseDto(
                        OrderStatus.CONFIRMED.name(),
                        null,
                        toSnapshot(currentState)
                );
            } else {
                String reason = "Requested quantity (" + quantity + ") exceeds available stock ("
                        + currentState.getStock() + ")";
                orderRepository.save(new Order(productId, quantity, OrderStatus.REJECTED, reason));
                return new OrderResponseDto(
                        OrderStatus.REJECTED.name(),
                        reason,
                        toSnapshot(currentState)
                );
            }
        } catch (InventoryItemNotFoundException ex) {
            orderRepository.save(new Order(productId, quantity, OrderStatus.REJECTED, ex.getMessage()));
            return new OrderResponseDto(OrderStatus.REJECTED.name(), ex.getMessage(), null);
        }
    }

    private InventorySnapshotDto toSnapshot(InventoryItem item) {
        return new InventorySnapshotDto(item.getProductId(), item.getName(), item.getStock());
    }
}

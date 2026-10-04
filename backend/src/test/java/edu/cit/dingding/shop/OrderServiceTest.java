package edu.cit.dingding.shop;

import edu.cit.dingding.inventory.InventoryItem;
import edu.cit.dingding.inventory.InventoryService;
import edu.cit.dingding.shop.dto.OrderLineItemDto;
import edu.cit.dingding.shop.dto.OrderResponseDto;
import edu.cit.dingding.supplier.SupplierGateway;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    @Test
    void aggregatesDuplicateProductLinesBeforeCheckingStock() {
        InventoryService inventoryService = mock(InventoryService.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryService.lockItem("P100")).thenReturn(new InventoryItem("P100", "Mouse", 5));
        when(inventoryService.getItem("P100")).thenReturn(new InventoryItem("P100", "Mouse", 5));

        OrderService service = new OrderService(inventoryService, mock(SupplierGateway.class), orderRepository,
                orderItemRepository, mock(ApplicationEventPublisher.class));
        OrderResponseDto response = service.placeOrder(List.of(line("P100", 4), line("P100", 4)));

        assertEquals("REJECTED", response.getStatus());
        assertTrue(response.getReason().contains("Requested quantity (8)"));
        assertEquals(2, response.getItems().size());
        assertFalse(response.getItems().stream().anyMatch(item -> "RESERVED".equals(item.getOutcome())));
        verify(inventoryService, never()).reserve("P100", 4);
    }

    @Test
    void reportsWhetherCancellationActuallyRestocked() {
        InventoryService inventoryService = mock(InventoryService.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        Order backordered = new Order(OrderStatus.BACKORDERED, null);
        Order confirmed = new Order(OrderStatus.CONFIRMED, null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(backordered));
        when(orderRepository.findById(2L)).thenReturn(Optional.of(confirmed));
        when(orderItemRepository.findByOrderId(2L)).thenReturn(List.of(new OrderItem(2L, "P100", 3)));

        OrderService service = new OrderService(inventoryService, mock(SupplierGateway.class), orderRepository,
                orderItemRepository, mock(ApplicationEventPublisher.class));

        assertFalse(service.cancelOrder(1L));
        assertTrue(service.cancelOrder(2L));
        verify(inventoryService).restock("P100", 3);
    }

    private static OrderLineItemDto line(String productId, int quantity) {
        OrderLineItemDto line = new OrderLineItemDto();
        line.setProductId(productId);
        line.setQuantity(quantity);
        return line;
    }
}
package edu.cit.dingding.shop;

import edu.cit.dingding.shop.dto.OrderRequestDto;
import edu.cit.dingding.shop.dto.OrderResponseDto;
import edu.cit.dingding.shop.dto.OrderSummaryDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/orders")
    public OrderResponseDto placeOrder(@Valid @RequestBody OrderRequestDto request) {
        return orderService.placeOrder(request.getItems());
    }

    @GetMapping("/api/orders")
    public List<OrderSummaryDto> listOrders() {
        return orderService.getAllOrders();
    }

    @PostMapping("/api/orders/{orderId}/cancel")
    public Map<String, Object> cancelOrder(@PathVariable Long orderId) {
        orderService.cancelOrder(orderId);
        return Map.of("orderId", orderId, "status", "CANCELLED");
    }
}

package edu.cit.dingding.shop;

import edu.cit.dingding.shop.dto.OrderRequestDto;
import edu.cit.dingding.shop.dto.OrderResponseDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/orders")
    public OrderResponseDto placeOrder(@Valid @RequestBody OrderRequestDto request) {
        return orderService.placeOrder(request.getProductId(), request.getQuantity());
    }
}

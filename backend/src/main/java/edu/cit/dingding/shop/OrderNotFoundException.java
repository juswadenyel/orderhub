package edu.cit.dingding.shop;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class OrderNotFoundException extends ResponseStatusException {
    public OrderNotFoundException(Long orderId) {
        super(HttpStatus.NOT_FOUND, "No order found with id " + orderId);
    }
}

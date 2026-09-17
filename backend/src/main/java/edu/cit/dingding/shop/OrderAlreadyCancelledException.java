package edu.cit.dingding.shop;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class OrderAlreadyCancelledException extends ResponseStatusException {
    public OrderAlreadyCancelledException(Long orderId) {
        super(HttpStatus.CONFLICT, "Order " + orderId + " is already cancelled");
    }
}

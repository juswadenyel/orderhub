package edu.cit.dingding.shop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class OrderRequestDto {

    @NotEmpty
    @Valid
    private List<OrderLineItemDto> items;

    public OrderRequestDto() {
    }

    public List<OrderLineItemDto> getItems() {
        return items;
    }

    public void setItems(List<OrderLineItemDto> items) {
        this.items = items;
    }
}

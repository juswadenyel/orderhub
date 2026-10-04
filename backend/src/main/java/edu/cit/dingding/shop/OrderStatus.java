package edu.cit.dingding.shop;

public enum OrderStatus {
    CONFIRMED,
    REJECTED,
    CANCELLED,
    /** Lab 4: short on stock right now, but a LegacySupply delivery is already in flight. */
    BACKORDERED
}

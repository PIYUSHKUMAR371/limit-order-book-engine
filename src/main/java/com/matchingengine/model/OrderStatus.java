package com.matchingengine.model;

/**
 * Lifecycle states of an order in the matching engine.
 */
public enum OrderStatus {
    PENDING,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED
}
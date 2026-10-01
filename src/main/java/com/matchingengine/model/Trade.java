package com.matchingengine.model;

/**
 * Immutable execution record generated when a matching engine trade occurs.
 * Represented as a Java 17 record for thread-safety, zero boilerplate,
 * and compiler-level memory optimization.
 *
 * @param tradeId       Unique sequence identifier for the trade event
 * @param buyOrderId    ID of the resting or incoming buy order
 * @param sellOrderId   ID of the resting or incoming sell order
 * @param price         Execution price in fixed-point integer units (e.g. 10050 = $100.50)
 * @param quantity      Matched volume traded
 * @param timestamp     High-resolution execution timestamp (System.nanoTime())
 */
public record Trade(
    long tradeId,
    long buyOrderId,
    long sellOrderId,
    long price,
    long quantity,
    long timestamp
) {
    /**
     * Compact constructor to enforce strict validation rules upon creation.
     */
    public Trade {
        if (tradeId <= 0) {
            throw new IllegalArgumentException("Trade ID must be positive");
        }
        if (buyOrderId <= 0 || sellOrderId <= 0) {
            throw new IllegalArgumentException("Order IDs must be positive");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("Trade price must be positive");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Trade quantity must be positive");
        }
    }
}

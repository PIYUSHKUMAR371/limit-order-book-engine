package com.matchingengine.model;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * High-performance, thread-safe Order domain model.
 * Uses fixed-point primitive arithmetic for zero-GC pricing
 * and lock-free atomic primitives for concurrent state updates.
 */
public class Order {
    // Immutable core attributes (stack-allocated primitive / immutable enum)
    private final long orderId;
    private final Side side;
    private final long price;           // Stored in fixed-point cents/ticks (e.g. 10050 = $100.50)
    private final long initialQuantity;
    private final long timestamp;       // System.nanoTime() for Price-Time Priority ordering

    // Thread-safe mutable fields updated via CPU Compare-And-Swap (CAS) instructions
    private final AtomicLong remainingQuantity;
    private final AtomicReference<OrderStatus> status;

    public Order(long orderId, Side side, long price, long initialQuantity) {
        if (price <= 0) {
            throw new IllegalArgumentException("Price must be strictly positive");
        }
        if (initialQuantity <= 0) {
            throw new IllegalArgumentException("Quantity must be strictly positive");
        }

        this.orderId = orderId;
        this.side = side;
        this.price = price;
        this.initialQuantity = initialQuantity;
        this.timestamp = System.nanoTime();
        this.remainingQuantity = new AtomicLong(initialQuantity);
        this.status = new AtomicReference<>(OrderStatus.PENDING);
    }

    // Getters for immutable state
    public long getOrderId() { return orderId; }
    public Side getSide() { return side; }
    public long getPrice() { return price; }
    public long getInitialQuantity() { return initialQuantity; }
    public long getTimestamp() { return timestamp; }

    // Atomic read operations
    public long getRemainingQuantity() { return remainingQuantity.get(); }
    public OrderStatus getStatus() { return status.get(); }

    /**
     * Safely reduces remaining quantity using a lock-free CAS loop.
     * Prevents race conditions during concurrent fills.
     * 
     * @param fillQuantity number of units matched/filled
     * @return true if fill was applied, false if fill is invalid or order inactive
     */
    public boolean decrementQuantity(long fillQuantity) {
        if (fillQuantity <= 0) {
            return false;
        }

        // Lock-free optimistic retry loop
        while (true) {
            OrderStatus currentStatus = status.get();
            // Reject fill if order is already completely filled or cancelled
            if (currentStatus == OrderStatus.FILLED || currentStatus == OrderStatus.CANCELLED) {
                return false;
            }

            long currentQty = remainingQuantity.get();
            if (currentQty < fillQuantity) {
                return false; // Cannot over-fill an order
            }

            long newQty = currentQty - fillQuantity;
            
            // Atomic CAS operation: updates remainingQuantity ONLY if value hasn't changed concurrently
            if (remainingQuantity.compareAndSet(currentQty, newQty)) {
                OrderStatus newStatus = (newQty == 0) ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
                status.set(newStatus);
                return true;
            }
        }
    }

    /**
     * Attempts lock-free cancellation of the order.
     * 
     * @return true if successfully cancelled, false if order is already FILLED or CANCELLED
     */
    public boolean cancel() {
        while (true) {
            OrderStatus currentStatus = status.get();
            if (currentStatus == OrderStatus.FILLED || currentStatus == OrderStatus.CANCELLED) {
                return false; // Order cannot be cancelled once filled or previously cancelled
            }
            
            // Atomic CAS: transitions status to CANCELLED atomically
            if (status.compareAndSet(currentStatus, OrderStatus.CANCELLED)) {
                return true;
            }
        }
    }
}

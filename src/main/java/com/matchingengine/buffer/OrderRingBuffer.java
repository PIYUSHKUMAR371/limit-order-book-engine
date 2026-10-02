package com.matchingengine.buffer;

import com.matchingengine.model.Order;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * High-throughput, lock-free bounded Ring Buffer for order event ingress.
 * Uses power-of-two capacity for O(1) bitwise index masking and lock-free
 * CAS sequence tracking.
 */
public class OrderRingBuffer {
    private final AtomicReferenceArray<Order> buffer;
    private final int capacity;
    private final int mask;

    // Sequence counters for ring buffer boundary tracking
    private final AtomicLong head = new AtomicLong(0L); // Published position (producers)
    private final AtomicLong tail = new AtomicLong(0L); // Consumed position (engine thread)

    public OrderRingBuffer(int capacity) {
        // Enforce power-of-two capacity for fast bitwise masking
        if (capacity <= 0 || (capacity & (capacity - 1)) != 0) {
            throw new IllegalArgumentException("Capacity must be a positive power of two (e.g. 1024, 4096, 65536)");
        }
        this.capacity = capacity;
        this.mask = capacity - 1; // Bitmask equivalent to % capacity
        this.buffer = new AtomicReferenceArray<>(capacity);
    }

    public int getCapacity() {
        return capacity;
    }

    /**
     * Non-blocking attempt to publish an order onto the ring buffer.
     *
     * @param order incoming order request
     * @return true if successfully published, false if buffer is currently full
     */
    public boolean offer(Order order) {
        if (order == null) {
            throw new NullPointerException("Order cannot be null");
        }

        while (true) {
            long currentHead = head.get();
            long currentTail = tail.get();

            // Check if buffer is full
            if (currentHead - currentTail >= capacity) {
                return false; // Buffer overflow protection
            }

            // Lock-free claim of next available sequence slot
            if (head.compareAndSet(currentHead, currentHead + 1)) {
                int index = (int) (currentHead & mask); // Fast bitwise masking instead of % capacity
                buffer.set(index, order);
                return true;
            }
        }
    }

    /**
     * Non-blocking poll invoked by the single-writer matching engine thread.
     *
     * @return next order to process, or null if buffer is empty
     */
    public Order poll() {
        long currentTail = tail.get();
        long currentHead = head.get();

        if (currentTail >= currentHead) {
            return null; // Buffer empty
        }

        int index = (int) (currentTail & mask);
        Order order = buffer.get(index);

        if (order != null) {
            buffer.set(index, null); // Clear slot reference for GC
            tail.lazySet(currentTail + 1); // Ordered store for performance
            return order;
        }

        return null;
    }

    /**
     * Returns current number of unconsumed orders waiting in the buffer.
     */
    public int size() {
        return (int) (head.get() - tail.get());
    }

    public boolean isEmpty() {
        return size() == 0;
    }
}
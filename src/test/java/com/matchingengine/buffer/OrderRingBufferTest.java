package com.matchingengine.buffer;

import com.matchingengine.buffer.OrderRingBuffer;
import com.matchingengine.model.Order;
import com.matchingengine.model.Side;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderRingBufferTest {

    @Test
    @DisplayName("Should throw IllegalArgumentException if capacity is not a power of two")
    void testInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new OrderRingBuffer(100)); // 100 is not power of 2
        assertThrows(IllegalArgumentException.class, () -> new OrderRingBuffer(-16));
        assertNotNull(new OrderRingBuffer(1024)); // Valid power of 2
    }

    @Test
    @DisplayName("Should publish and consume orders in FIFO order")
    void testOfferAndPoll() {
        OrderRingBuffer ringBuffer = new OrderRingBuffer(4);

        Order order1 = new Order(101L, Side.BUY, 10000L, 10L);
        Order order2 = new Order(102L, Side.SELL, 10100L, 20L);

        assertTrue(ringBuffer.offer(order1));
        assertTrue(ringBuffer.offer(order2));
        assertEquals(2, ringBuffer.size());

        assertEquals(order1, ringBuffer.poll());
        assertEquals(order2, ringBuffer.poll());
        assertNull(ringBuffer.poll()); // Buffer now empty
        assertTrue(ringBuffer.isEmpty());
    }

    @Test
    @DisplayName("Should reject offer when ring buffer reaches maximum capacity")
    void testBufferOverflow() {
        OrderRingBuffer ringBuffer = new OrderRingBuffer(2); // Small capacity for test

        Order order1 = new Order(1L, Side.BUY, 10000L, 10L);
        Order order2 = new Order(2L, Side.BUY, 10000L, 10L);
        Order order3 = new Order(3L, Side.BUY, 10000L, 10L);

        assertTrue(ringBuffer.offer(order1));
        assertTrue(ringBuffer.offer(order2));

        // Buffer full, offer must fail
        assertFalse(ringBuffer.offer(order3));
        assertEquals(2, ringBuffer.size());

        // After polling one element, offer should succeed again
        assertNotNull(ringBuffer.poll());
        assertTrue(ringBuffer.offer(order3));
    }
}

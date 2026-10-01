package com.matchingengine.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    @DisplayName("Should initialize order fields correctly with valid input values")
    void testOrderInitialization() {
        Order order = new Order(101L, Side.BUY, 15025L, 100L);

        assertEquals(101L, order.getOrderId());
        assertEquals(Side.BUY, order.getSide());
        assertEquals(15025L, order.getPrice());
        assertEquals(100L, order.getInitialQuantity());
        assertEquals(100L, order.getRemainingQuantity());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertTrue(order.getTimestamp() > 0);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when initialized with non-positive price or quantity")
    void testInvalidInitialization() {
        assertThrows(IllegalArgumentException.class, () -> new Order(1L, Side.BUY, 0L, 100L));
        assertThrows(IllegalArgumentException.class, () -> new Order(1L, Side.BUY, 100L, -5L));
    }

    @Test
    @DisplayName("Should transition status to PARTIALLY_FILLED and FILLED across progressive decrements")
    void testPartialAndFullFills() {
        Order order = new Order(102L, Side.SELL, 20000L, 50L);

        // Partial Fill
        boolean partialResult = order.decrementQuantity(20L);
        assertTrue(partialResult);
        assertEquals(30L, order.getRemainingQuantity());
        assertEquals(OrderStatus.PARTIALLY_FILLED, order.getStatus());

        // Full Fill
        boolean fullResult = order.decrementQuantity(30L);
        assertTrue(fullResult);
        assertEquals(0L, order.getRemainingQuantity());
        assertEquals(OrderStatus.FILLED, order.getStatus());

        // Over-fill attempt should fail
        boolean overfillResult = order.decrementQuantity(10L);
        assertFalse(overfillResult);
    }

    @Test
    @DisplayName("Should successfully cancel pending order and reject post-cancellation fill attempts")
    void testCancellationLifecycle() {
        Order order = new Order(103L, Side.BUY, 10000L, 40L);

        assertTrue(order.cancel());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());

        // Repeated cancel should return false
        assertFalse(order.cancel());

        // Decrements on cancelled orders must fail
        assertFalse(order.decrementQuantity(10L));
    }

    @Test
    @DisplayName("Should reject cancellation if order is already completely filled")
    void testCancelFilledOrder() {
        Order order = new Order(104L, Side.SELL, 10000L, 10L);
        order.decrementQuantity(10L);
        assertEquals(OrderStatus.FILLED, order.getStatus());

        assertFalse(order.cancel());
        assertEquals(OrderStatus.FILLED, order.getStatus());
    }
}
package com.matchingengine.book;

import com.matchingengine.book.OrderBook;
import com.matchingengine.model.Order;
import com.matchingengine.model.OrderStatus;
import com.matchingengine.model.Side;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    @Test
    @DisplayName("Should maintain Bids in descending price order (Highest Price First)")
    void testBidPricePriority() {
        OrderBook book = new OrderBook("AAPL");

        Order lowerBid = new Order(1L, Side.BUY, 10000L, 10L); // $100.00
        Order higherBid = new Order(2L, Side.BUY, 10500L, 10L); // $105.00

        book.addOrder(lowerBid);
        book.addOrder(higherBid);

        assertEquals(10500L, book.getBestBidPrice());
        assertEquals(2, book.getBidDepth());
    }

    @Test
    @DisplayName("Should maintain Asks in ascending price order (Lowest Price First)")
    void testAskPricePriority() {
        OrderBook book = new OrderBook("AAPL");

        Order higherAsk = new Order(1L, Side.SELL, 20000L, 10L); // $200.00
        Order lowerAsk = new Order(2L, Side.SELL, 19500L, 10L);  // $195.00

        book.addOrder(higherAsk);
        book.addOrder(lowerAsk);

        assertEquals(19500L, book.getBestAskPrice());
        assertEquals(2, book.getAskDepth());
    }

    @Test
    @DisplayName("Should preserve FIFO Time Priority for orders submitted at identical price level")
    void testTimePriorityFIFO() {
        OrderBook book = new OrderBook("AAPL");

        Order firstOrder = new Order(101L, Side.BUY, 15000L, 50L);
        Order secondOrder = new Order(102L, Side.BUY, 15000L, 30L);

        book.addOrder(firstOrder);
        book.addOrder(secondOrder);

        ConcurrentLinkedQueue<Order> queueAt15000 = book.getBids().get(15000L);
        assertNotNull(queueAt15000);
        
        // First order inserted must be polled first
        assertEquals(firstOrder, queueAt15000.peek());
    }


    @Test
    @DisplayName("Should cancel pending order in O(1) and update order status")
    void testCancelOrderSuccess() {
        OrderBook book = new OrderBook("AAPL");
        Order order = new Order(201L, Side.BUY, 10000L, 50L);

        book.addOrder(order);
        assertNotNull(book.getOrder(201L));

        boolean cancelled = book.cancelOrder(201L);
        assertTrue(cancelled);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertNull(book.getOrder(201L)); // Removed from fast lookup map
    }

    @Test
    @DisplayName("Should return false when attempting to cancel non-existent or previously cancelled order")
    void testCancelOrderFailure() {
        OrderBook book = new OrderBook("AAPL");
        Order order = new Order(202L, Side.SELL, 15000L, 20L);

        book.addOrder(order);

        // First cancellation succeeds
        assertTrue(book.cancelOrder(202L));

        // Second cancellation on same ID fails
        assertFalse(book.cancelOrder(202L));

        // Cancelling non-existent ID fails
        assertFalse(book.cancelOrder(9999L));
    }
}
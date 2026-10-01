package com.matchingengine.book;

import com.matchingengine.model.Order;
import com.matchingengine.model.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentLinkedQueue;

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
}
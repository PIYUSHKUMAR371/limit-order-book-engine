package com.matchingengine.dispatcher;

import com.matchingengine.book.OrderBook;
import com.matchingengine.dispatcher.EngineDispatcher;
import com.matchingengine.engine.MatchingEngine;
import com.matchingengine.model.Order;
import com.matchingengine.model.Side;
import com.matchingengine.model.Trade;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static java.util.concurrent.TimeUnit.SECONDS;

class EngineDispatcherTest {

    private OrderBook orderBook;
    private MatchingEngine engine;
    private EngineDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook("AAPL");
        engine = new MatchingEngine(orderBook);
        dispatcher = new EngineDispatcher(engine, 1024);
        dispatcher.start();
    }

    @AfterEach
    void tearDown() {
        dispatcher.stop();
    }

    @Test
    @DisplayName("Should process submitted orders asynchronously and generate executions")
    void testAsyncOrderProcessing() {
        Order ask = new Order(101L, Side.SELL, 15000L, 20L);
        Order bid = new Order(102L, Side.BUY, 15000L, 20L);

        assertTrue(dispatcher.submit(ask));
        assertTrue(dispatcher.submit(bid));

        // Wait for async worker thread to complete execution
        await().atMost(2, SECONDS).until(() -> dispatcher.getExecutedTrades().size() == 1);

        List<Trade> trades = dispatcher.getExecutedTrades();
        assertEquals(1, trades.size());
        assertEquals(15000L, trades.get(0).price());
        assertEquals(20L, trades.get(0).quantity());
    }

    @Test
    @DisplayName("Should verify worker thread is running and stops cleanly")
    void testDispatcherLifecycle() {
        assertTrue(dispatcher.isRunning());
        dispatcher.stop();
        assertFalse(dispatcher.isRunning());
    }
}
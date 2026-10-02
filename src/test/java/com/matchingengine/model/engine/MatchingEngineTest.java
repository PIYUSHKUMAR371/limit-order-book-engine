package com.matchingengine.engine;

import com.matchingengine.book.OrderBook;
import com.matchingengine.model.Order;
import com.matchingengine.model.OrderStatus;
import com.matchingengine.model.Side;
import com.matchingengine.model.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {

    private OrderBook orderBook;
    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook("AAPL");
        engine = new MatchingEngine(orderBook);
    }

    @Test
    @DisplayName("Should execute complete match when incoming Buy matches resting Sell exact quantity")
    void testExactMatchExecution() {
        Order restingSell = new Order(101L, Side.SELL, 15000L, 50L);
        orderBook.addOrder(restingSell);

        Order incomingBuy = new Order(102L, Side.BUY, 15000L, 50L);
        List<Trade> trades = engine.processOrder(incomingBuy);

        assertEquals(1, trades.size());
        Trade trade = trades.get(0);

        assertEquals(102L, trade.buyOrderId());
        assertEquals(101L, trade.sellOrderId());
        assertEquals(15000L, trade.price());
        assertEquals(50L, trade.quantity());

        assertEquals(OrderStatus.FILLED, restingSell.getStatus());
        assertEquals(OrderStatus.FILLED, incomingBuy.getStatus());
        assertNull(orderBook.getBestAskPrice()); // Ask queue cleared
    }

    @Test
    @DisplayName("Should execute partial match and leave unfilled incoming quantity resting in order book")
    void testPartialMatchAndRestingRemainder() {
        Order restingAsk = new Order(201L, Side.SELL, 10000L, 30L);
        orderBook.addOrder(restingAsk);

        Order incomingBuy = new Order(202L, Side.BUY, 10000L, 100L); // Requests 100, only 30 available
        List<Trade> trades = engine.processOrder(incomingBuy);

        assertEquals(1, trades.size());
        assertEquals(30L, trades.get(0).quantity());

        assertEquals(OrderStatus.FILLED, restingAsk.getStatus());
        assertEquals(OrderStatus.PARTIALLY_FILLED, incomingBuy.getStatus());
        assertEquals(70L, incomingBuy.getRemainingQuantity());

        // Unfilled 70 units should now rest on the Bid side
        assertEquals(10000L, orderBook.getBestBidPrice());
    }

    @Test
    @DisplayName("Should skip cancelled resting orders during execution (Lazy Cleanup)")
    void testSkipCancelledRestingOrder() {
        Order restingAsk = new Order(301L, Side.SELL, 10000L, 50L);
        orderBook.addOrder(restingAsk);

        // Cancel resting ask before buy arrives
        orderBook.cancelOrder(301L);

        Order incomingBuy = new Order(302L, Side.BUY, 10000L, 50L);
        List<Trade> trades = engine.processOrder(incomingBuy);

        assertTrue(trades.isEmpty()); // No trades executed against cancelled order
        assertEquals(10000L, orderBook.getBestBidPrice()); // Incoming buy rests in book
    }
}
package com.matchingengine.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradeTest {

    @Test
    @DisplayName("Should successfully construct an immutable Trade record with valid parameters")
    void testValidTradeCreation() {
        long now = System.nanoTime();
        Trade trade = new Trade(1001L, 501L, 502L, 15000L, 25L, now);

        assertEquals(1001L, trade.tradeId());
        assertEquals(501L, trade.buyOrderId());
        assertEquals(502L, trade.sellOrderId());
        assertEquals(15000L, trade.price());
        assertEquals(25L, trade.quantity());
        assertEquals(now, trade.timestamp());
    }

    @Test
    @DisplayName("Should enforce field validation and throw IllegalArgumentException on invalid input")
    void testInvalidTradeCreation() {
        long now = System.nanoTime();

        assertThrows(IllegalArgumentException.class, () ->
            new Trade(-1L, 501L, 502L, 15000L, 25L, now)
        );

        assertThrows(IllegalArgumentException.class, () ->
            new Trade(1001L, 0L, 502L, 15000L, 25L, now)
        );

        assertThrows(IllegalArgumentException.class, () ->
            new Trade(1001L, 501L, 502L, -100L, 25L, now)
        );

        assertThrows(IllegalArgumentException.class, () ->
            new Trade(1001L, 501L, 502L, 15000L, 0L, now)
        );
    }

    @Test
    @DisplayName("Should satisfy record immutability and equality contracts")
    void testRecordEquality() {
        long now = System.nanoTime();
        Trade trade1 = new Trade(1001L, 501L, 502L, 15000L, 25L, now);
        Trade trade2 = new Trade(1001L, 501L, 502L, 15000L, 25L, now);

        assertEquals(trade1, trade2);
        assertEquals(trade1.hashCode(), trade2.hashCode());
    }
}
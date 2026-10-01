package com.matchingengine.book;

import com.matchingengine.model.Order;
import com.matchingengine.model.Side;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * High-throughput thread-safe OrderBook maintaining Price-Time Priority.
 * Uses ConcurrentSkipListMap for O(log N) price sorting and ConcurrentLinkedQueue
 * for O(1) FIFO time priority per price level.
 */
public class OrderBook {
    private final String symbol;

    // Bids: Sorted Descending (Highest Price First)
    private final ConcurrentSkipListMap<Long, ConcurrentLinkedQueue<Order>> bids;

    // Asks: Sorted Ascending (Lowest Price First)
    private final ConcurrentSkipListMap<Long, ConcurrentLinkedQueue<Order>> asks;

    // Fast O(1) lookup map for order cancellation/lookup by orderId
    private final Map<Long, Order> orderLookup;

    public OrderBook(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be empty");
        }
        this.symbol = symbol;
        
        // Reverse order comparator guarantees highest bid is first Key
        this.bids = new ConcurrentSkipListMap<>(Comparator.reverseOrder());
        
        // Natural order comparator guarantees lowest ask is first Key
        this.asks = new ConcurrentSkipListMap<>();
        
        this.orderLookup = new ConcurrentHashMap<>();
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * Adds an order into the order book preserving price-time priority.
     * 
     * @param order incoming order to add
     */
    public void addOrder(Order order) {
        orderLookup.put(order.getOrderId(), order);
        
        ConcurrentSkipListMap<Long, ConcurrentLinkedQueue<Order>> targetSideMap = 
            (order.getSide() == Side.BUY) ? bids : asks;

        // computeIfAbsent is atomic in ConcurrentSkipListMap
        targetSideMap.computeIfAbsent(order.getPrice(), k -> new ConcurrentLinkedQueue<>())
                     .add(order);
    }

    /**
     * Retrieves the best (highest) active Bid price level, or null if no bids exist.
     */
    public Long getBestBidPrice() {
        return bids.isEmpty() ? null : bids.firstKey();
    }

    /**
     * Retrieves the best (lowest) active Ask price level, or null if no asks exist.
     */
    public Long getBestAskPrice() {
        return asks.isEmpty() ? null : asks.firstKey();
    }

    /**
     * Returns the number of order queues currently active on the Bid side.
     */
    public int getBidDepth() {
        return bids.size();
    }

    /**
     * Returns the number of order queues currently active on the Ask side.
     */
    public int getAskDepth() {
        return asks.size();
    }

    /**
     * Fetches an order by its ID in O(1) time.
     */
    public Order getOrder(long orderId) {
        return orderLookup.get(orderId);
    }

    public ConcurrentSkipListMap<Long, ConcurrentLinkedQueue<Order>> getBids() {
        return bids;
    }

    public ConcurrentSkipListMap<Long, ConcurrentLinkedQueue<Order>> getAsks() {
        return asks;
    }
}

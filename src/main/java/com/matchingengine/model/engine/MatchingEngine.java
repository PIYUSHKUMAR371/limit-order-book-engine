package com.matchingengine.engine;

import com.matchingengine.book.OrderBook;
import com.matchingengine.model.Order;
import com.matchingengine.model.OrderStatus;
import com.matchingengine.model.Side;
import com.matchingengine.model.Trade;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core Price-Time Priority Matching Engine Kernel.
 * Executes trades atomically against resting book liquidity and publishes Trade records.
 */
public class MatchingEngine {
    private final OrderBook orderBook;
    private final AtomicLong tradeIdSequence;

    public MatchingEngine(OrderBook orderBook) {
        if (orderBook == null) {
            throw new IllegalArgumentException("OrderBook cannot be null");
        }
        this.orderBook = orderBook;
        this.tradeIdSequence = new AtomicLong(1L);
    }

    public OrderBook getOrderBook() {
        return orderBook;
    }

    /**
     * Processes an incoming order. Matches immediately against resting orders if spread crosses,
     * or adds remaining quantity to the order book.
     *
     * @param incomingOrder incoming buy or sell order
     * @return list of generated Trade execution records
     */
    public List<Trade> processOrder(Order incomingOrder) {
        List<Trade> trades = new ArrayList<>();

        if (incomingOrder.getSide() == Side.BUY) {
            matchBuyOrder(incomingOrder, trades);
        } else {
            matchSellOrder(incomingOrder, trades);
        }

        // If incoming order still has unfilled quantity, place remainder into the book
        if (incomingOrder.getRemainingQuantity() > 0 && incomingOrder.getStatus() != OrderStatus.CANCELLED) {
            orderBook.addOrder(incomingOrder);
        }

        return trades;
    }

    private void matchBuyOrder(Order buyOrder, List<Trade> trades) {
        ConcurrentSkipListMap<Long, ConcurrentLinkedQueue<Order>> asks = orderBook.getAsks();

        while (buyOrder.getRemainingQuantity() > 0 && !asks.isEmpty()) {
            Long bestAskPrice = asks.firstKey();

            // Buy Price < Best Ask Price -> Spread not crossed, stop matching
            if (buyOrder.getPrice() < bestAskPrice) {
                break;
            }

            ConcurrentLinkedQueue<Order> askQueue = asks.get(bestAskPrice);
            if (askQueue == null || askQueue.isEmpty()) {
                asks.remove(bestAskPrice);
                continue;
            }

            matchAgainstQueue(buyOrder, askQueue, bestAskPrice, trades);

            // Clean up ask price level if queue becomes empty
            if (askQueue.isEmpty()) {
                asks.remove(bestAskPrice, askQueue);
            }
        }
    }

    private void matchSellOrder(Order sellOrder, List<Trade> trades) {
        ConcurrentSkipListMap<Long, ConcurrentLinkedQueue<Order>> bids = orderBook.getBids();

        while (sellOrder.getRemainingQuantity() > 0 && !bids.isEmpty()) {
            Long bestBidPrice = bids.firstKey();

            // Sell Price > Best Bid Price -> Spread not crossed, stop matching
            if (sellOrder.getPrice() > bestBidPrice) {
                break;
            }

            ConcurrentLinkedQueue<Order> bidQueue = bids.get(bestBidPrice);
            if (bidQueue == null || bidQueue.isEmpty()) {
                bids.remove(bestBidPrice);
                continue;
            }

            matchAgainstQueue(sellOrder, bidQueue, bestBidPrice, trades);

            // Clean up bid price level if queue becomes empty
            if (bidQueue.isEmpty()) {
                bids.remove(bestBidPrice, bidQueue);
            }
        }
    }

    private void matchAgainstQueue(Order incoming, ConcurrentLinkedQueue<Order> restingQueue, long executionPrice, List<Trade> trades) {
        while (incoming.getRemainingQuantity() > 0 && !restingQueue.isEmpty()) {
            Order resting = restingQueue.peek();

            if (resting == null) {
                restingQueue.poll();
                continue;
            }

            // Lazy Cleanup: Skip resting orders that were cancelled or fully filled concurrently
            if (resting.getStatus() == OrderStatus.CANCELLED || resting.getRemainingQuantity() == 0) {
                restingQueue.poll();
                continue;
            }

            long matchQuantity = Math.min(incoming.getRemainingQuantity(), resting.getRemainingQuantity());

            // Attempt atomic quantity decrements on resting order first
            if (resting.decrementQuantity(matchQuantity)) {
                incoming.decrementQuantity(matchQuantity);

                long buyOrderId = (incoming.getSide() == Side.BUY) ? incoming.getOrderId() : resting.getOrderId();
                long sellOrderId = (incoming.getSide() == Side.SELL) ? incoming.getOrderId() : resting.getOrderId();

                Trade trade = new Trade(
                    tradeIdSequence.getAndIncrement(),
                    buyOrderId,
                    sellOrderId,
                    executionPrice, // Execution happens at resting order's price
                    matchQuantity,
                    System.nanoTime()
                );

                trades.add(trade);

                // If resting order is fully executed, remove it from queue head
                if (resting.getRemainingQuantity() == 0) {
                    restingQueue.poll();
                }
            } else {
                // Decrement failed (e.g. resting order was cancelled concurrently), evict from queue
                restingQueue.poll();
            }
        }
    }
}

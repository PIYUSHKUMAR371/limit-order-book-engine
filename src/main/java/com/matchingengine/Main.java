package com.matchingengine;

import com.matchingengine.book.OrderBook;
import com.matchingengine.dispatcher.EngineDispatcher;
import com.matchingengine.engine.MatchingEngine;
import com.matchingengine.model.Order;
import com.matchingengine.model.Side;
import com.matchingengine.model.Trade;

import java.util.List;

/**
 * Live demonstration runner for the High-Throughput Limit Order Book Engine.
 */
public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=================================================");
        System.out.println("   HIGH-THROUGHPUT LIMIT ORDER BOOK ENGINE");
        System.out.println("=================================================\n");

        // 1. Initialize OrderBook, Engine Kernel, and Lock-Free Dispatcher
        OrderBook orderBook = new OrderBook("AAPL");
        MatchingEngine matchingEngine = new MatchingEngine(orderBook);
        EngineDispatcher dispatcher = new EngineDispatcher(matchingEngine, 1024);

        System.out.println("[INFO] Starting EngineDispatcher single-writer thread...");
        dispatcher.start();

        // 2. Seed resting Sell Liquidity (Asks)
        System.out.println("[INFO] Submitting resting Ask orders (Sell side)...");
        dispatcher.submit(new Order(101L, Side.SELL, 15000L, 50L)); // 50 @ $150.00
        dispatcher.submit(new Order(102L, Side.SELL, 15050L, 100L)); // 100 @ $150.50
        dispatcher.submit(new Order(103L, Side.SELL, 15100L, 200L)); // 200 @ $151.00

        Thread.sleep(100); // Allow worker thread to ingest

        System.out.println("\n--- LIVE ORDER BOOK DEPTH (BEFORE MATCH) ---");
        System.out.println("Best Ask Price: " + orderBook.getBestAskPrice() + " cents ($150.00)");
        System.out.println("Ask Depth Levels: " + orderBook.getAskDepth());

        // 3. Submit Aggressive Incoming Buy Order that crosses the spread
        System.out.println("\n[INFO] Submitting Aggressive Buy Order (120 units @ $150.50)...");
        Order incomingBuy = new Order(201L, Side.BUY, 15050L, 120L);
        dispatcher.submit(incomingBuy);

        // Wait briefly for asynchronous execution
        Thread.sleep(200);

        // 4. Print Executed Trades
        System.out.println("\n=================================================");
        System.out.println("             EXECUTED TRADE STREAM");
        System.out.println("=================================================");
        List<Trade> trades = dispatcher.getExecutedTrades();
        for (Trade trade : trades) {
            System.out.printf("TRADE EXECUTED -> ID: %d | BuyOrder: %d | SellOrder: %d | Price: %d cents | Qty: %d\n",
                    trade.tradeId(), trade.buyOrderId(), trade.sellOrderId(), trade.price(), trade.quantity());
        }

        // 5. Print Updated Book State
        System.out.println("\n--- LIVE ORDER BOOK DEPTH (AFTER MATCH) ---");
        System.out.println("Best Ask Price: " + orderBook.getBestAskPrice() + " cents ($150.50)");
        System.out.println("Remaining Unfilled Buy Qty: " + incomingBuy.getRemainingQuantity());

        System.out.println("\n[INFO] Shutting down engine cleanly...");
        dispatcher.stop();
        System.out.println("[SUCCESS] Engine simulation completed.");
    }
}

package com.matchingengine.benchmark;

import com.matchingengine.book.OrderBook;
import com.matchingengine.engine.MatchingEngine;
import com.matchingengine.model.Order;
import com.matchingengine.model.Side;
import com.matchingengine.model.Trade;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JMH Microbenchmark suite measuring throughput and latency
 * of the core Matching Engine kernel.
 */
@BenchmarkMode(Mode.Throughput) // Measures ops/sec
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class OrderBookBenchmark {

    private OrderBook orderBook;
    private MatchingEngine matchingEngine;
    private AtomicLong orderIdSequence;

    @Setup(Level.Trial)
    public void setupTrial() {
        orderBook = new OrderBook("AAPL");
        matchingEngine = new MatchingEngine(orderBook);
        orderIdSequence = new AtomicLong(1000L);

        // Pre-populate resting liquidity in the book (Depth setup)
        for (int i = 0; i < 500; i++) {
            Order ask = new Order(i + 1, Side.SELL, 10000L + (i * 5), 100L);
            matchingEngine.processOrder(ask);
        }
    }

    /**
     * Measures the raw throughput of processing aggressive incoming buy orders 
     * against resting sell liquidity.
     */
    @Benchmark
    public void benchmarkOrderMatching(Blackhole blackhole) {
        long nextId = orderIdSequence.getAndIncrement();
        // Incoming buy crosses the spread with resting asks at 10000
        Order buyOrder = new Order(nextId, Side.BUY, 10000L, 10L);

        List<Trade> trades = matchingEngine.processOrder(buyOrder);

        // Blackhole consumes the trade output to prevent Dead Code Elimination (DCE)
        blackhole.consume(trades);
    }

    /**
     * Harness entry point allowing benchmark execution via standard java main.
     */
    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}
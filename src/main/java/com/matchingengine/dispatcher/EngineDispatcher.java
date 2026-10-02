package com.matchingengine.dispatcher;

import com.matchingengine.buffer.OrderRingBuffer;
import com.matchingengine.engine.MatchingEngine;
import com.matchingengine.model.Order;
import com.matchingengine.model.Trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Asynchronous Event Loop Dispatcher enforcing Single-Writer execution model.
 * Consumes orders from the ingress OrderRingBuffer and matches them continuously
 * on a dedicated high-priority thread.
 */
public class EngineDispatcher implements Runnable {
    private final OrderRingBuffer ringBuffer;
    private final MatchingEngine matchingEngine;
    private final List<Trade> executedTrades;

    private final Thread workerThread;
    private volatile boolean running = false;

    public EngineDispatcher(MatchingEngine matchingEngine, int bufferCapacity) {
        if (matchingEngine == null) {
            throw new IllegalArgumentException("MatchingEngine cannot be null");
        }
        this.matchingEngine = matchingEngine;
        this.ringBuffer = new OrderRingBuffer(bufferCapacity);
        this.executedTrades = new CopyOnWriteArrayList<>();
        this.workerThread = new Thread(this, "matching-engine-worker");
    }

    /**
     * Starts the dedicated single-writer event loop thread.
     */
    public synchronized void start() {
        if (!running) {
            running = true;
            workerThread.start();
        }
    }

    /**
     * Gracefully stops the dedicated worker thread and waits for completion.
     */
    public synchronized void stop() {
        if (running) {
            running = false;
            try {
                workerThread.join(2000); // Wait up to 2 seconds for worker thread to exit
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Non-blocking entry point for producer threads to submit orders.
     *
     * @param order incoming order request
     * @return true if accepted into ring buffer, false if buffer overflowed
     */
    public boolean submit(Order order) {
        return ringBuffer.offer(order);
    }

    @Override
    public void run() {
        while (running || !ringBuffer.isEmpty()) {
            Order order = ringBuffer.poll();

            if (order != null) {
                List<Trade> trades = matchingEngine.processOrder(order);
                if (!trades.isEmpty()) {
                    executedTrades.addAll(trades);
                }
            } else {
                // Low-latency CPU spin-wait hint (x86 PAUSE instruction)
                Thread.onSpinWait();
            }
        }
    }

    public List<Trade> getExecutedTrades() {
        return Collections.unmodifiableList(executedTrades);
    }

    public MatchingEngine getMatchingEngine() {
        return matchingEngine;
    }

    public boolean isRunning() {
        return running;
    }
}

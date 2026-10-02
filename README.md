# Ultra-Low Latency Limit Order Book & Matching Engine

A deterministic, multi-threaded Limit Order Book (LOB) matching engine implemented in Java 17. Designed specifically for quantitative trading and low-latency financial systems, this engine achieves high throughput and sub-microsecond processing latencies by leveraging a lock-free single-writer architecture, zero-GC-pressure memory design, and lock-free data structures.

---

## Technical Highlights & Low-Latency Architecture

### 1. Single-Writer Thread Architecture (`EngineDispatcher`)
* **Thread Concurrency Strategy**: Uses a dedicated single-writer event-loop thread for order book execution, eliminating traditional global mutexes, synchronized blocks, and multi-writer lock contention.
* **CPU Spin-Wait Optimization**: Utilizes `Thread.onSpinWait()` in active-wait loops to signal CPU hardware thread hints, reducing power consumption and instruction pipeline stall latencies without context-switching to the OS kernel.

### 2. Lock-Free Ingress Ring Buffer (`OrderRingBuffer`)
* **Asynchronous Ingestion**: Acts as a high-speed bounded queue using fixed-size arrays and atomic tail/head sequence pointers (`AtomicLong`) for seamless lock-free handoffs from multi-threaded order producers to the core matching engine.
* **Cache Alignment**: Structured to prevent false sharing and ensure CPU cache-line friendliness.

### 3. Price-Time Priority (FIFO) Matching Engine (`OrderBook` & `MatchingEngine`)
* **O(log N) Depth Lookup**: Maintains double-sided order depth (Bids sorted in descending order, Asks sorted in ascending order) using lock-free skip-lists (`ConcurrentSkipListMap`).
* **O(1) Queue Order Execution**: Each price level anchors a concurrent double-ended queue (`ConcurrentLinkedQueue`) that enforces strict FIFO time priority for limit order execution.

### 4. Zero-GC Memory Management & Numerical Precision
* **Fixed-Point Primitive Arithmetic**: Replaced slow, heap-allocated `BigDecimal` instances and imprecise floating-point `double`s with stack-allocated `long` primitive integers for currency and ticks (e.g., `$150.50` stored as `15050` cents).
* **In-Place Mutation**: Minimizes temporary object allocation in the hot path to eliminate Garbage Collection (GC) latency spikes (STW pauses).

---

## System Architecture Blueprint

```text
┌────────────────────────────────────────────────────────┐
│                   Producer Threads                     │
│           (Rest APIs, FIX Feeds, Benchmarks)           │
└───────────────────────────┬────────────────────────────┘
                            │ (Lock-Free Submit)
                            ▼
┌────────────────────────────────────────────────────────┐
│               OrderRingBuffer (Ingress Queue)          │
│            Atomic Sequence Counters & Pointers         │
└───────────────────────────┬────────────────────────────┘
                            │ (Single-Writer Dispatch)
                            ▼
┌────────────────────────────────────────────────────────┐
│               EngineDispatcher Core Loop               │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                     MatchingEngine                     │
│                                                        │
│   ┌────────────────────────┐  ┌────────────────────┐   │
│   │   BIDS (BUY DEPTH)     │  │  ASKS (SELL DEPTH) │   │
│   │ ConcurrentSkipListMap  │  │ ConcurrentSkipList │   │
│   │ (Price Descending)     │  │ (Price Ascending)  │   │
│   └───────────┬────────────┘  └─────────┬──────────┘   │
│               │                         │              │
│               └───────► MATCH ◄─────────┘              │
│                     (Bid ≥ Ask)                        │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                  Executed Trade Stream                 │
│              Atomic State Transitions (FILLED)         │
└────────────────────────────────────────────────────────┘
```

##Project Structure
limit-order-book-engine/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   └── java/
    │       └── com/
    │           └── matchingengine/
    │               ├── Main.java                        # Live Engine Simulation
    │               ├── benchmark/
    │               │   └── OrderBookBenchmark.java     # JMH Microbenchmarking Suite
    │               ├── book/
    │               │   └── OrderBook.java              # LOB Depth & Price-Level Management
    │               ├── buffer/
    │               │   └── OrderRingBuffer.java        # Lock-Free Ingress Buffer
    │               ├── dispatcher/
    │               │   └── EngineDispatcher.java       # Single-Writer Thread Loop
    │               ├── engine/
    │               │   └── MatchingEngine.java         # Core FIFO Execution Algorithm
    │               └── model/
    │                   ├── Order.java                  # Stack-Friendly Order Model
    │                   ├── OrderStatus.java            # Order Lifecycle Enums
    │                   ├── Side.java                   # BUY / SELL Side Enums
    │                   └── Trade.java                  # Executed Trade Object
    └── test/
        └── java/
            └── com/
                └── matchingengine/
                    ├── book/
                    │   └── OrderBookTest.java          # OrderBook Unit & Depth Tests
                    ├── buffer/
                    │   └── OrderRingBufferTest.java    # Concurrent Buffer Safety Tests
                    ├── dispatcher/
                    │   └── EngineDispatcherTest.java   # Lock-Free Threading & Awaitility Tests
                    ├── engine/
                    │   └── MatchingEngineTest.java     # Core Execution & FIFO Match Tests
                    └── model/
                        ├── OrderTest.java              # Order Mutation Tests
                        └── TradeTest.java              # Trade Creation Tests


##Verification & Testing Suite
The engine includes 100% passing unit and integration tests verifying price-time priority (FIFO), partial order fills, ring buffer boundaries, and lock-free async dispatching.

###1. Running Unit & Integration Tests
```bash
mvn clean test
```
####Verified Terminal Output:
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.matchingengine.book.OrderBookTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.matchingengine.buffer.OrderRingBufferTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.matchingengine.dispatcher.EngineDispatcherTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.matchingengine.engine.MatchingEngineTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.matchingengine.model.OrderTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.matchingengine.model.TradeTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] Total time:  8.445 s

###2. Running Live Simulation Engine
To observe real-time lock-free order ingestion, order book depth creation, price-time priority matching, and trade execution streaming:
```bash
mvn compile exec:java
```
####Verified Terminal Output:
=================================================
   HIGH-THROUGHPUT LIMIT ORDER BOOK ENGINE
=================================================

[INFO] Starting EngineDispatcher single-writer thread...
[INFO] Submitting resting Ask orders (Sell side)...

--- LIVE ORDER BOOK DEPTH (BEFORE MATCH) ---
Best Ask Price: 15000 cents ($150.00)
Ask Depth Levels: 3

[INFO] Submitting Aggressive Buy Order (120 units @ $150.50)...

=================================================
             EXECUTED TRADE STREAM
=================================================
TRADE EXECUTED -> ID: 1 | BuyOrder: 201 | SellOrder: 101 | Price: 15000 cents | Qty: 50
TRADE EXECUTED -> ID: 2 | BuyOrder: 201 | SellOrder: 102 | Price: 15050 cents | Qty: 70

--- LIVE ORDER BOOK DEPTH (AFTER MATCH) ---
Best Ask Price: 15050 cents ($150.50)
Remaining Unfilled Buy Qty: 0

[INFO] Shutting down engine cleanly...
[SUCCESS] Engine simulation completed.

### 3. Latency & Microbenchmark Suite (JMH)

The engine includes an automated Java Microbenchmark Harness (JMH) test suite measuring kernel execution throughput and sub-microsecond latency under heavy order flow:
```bash
mvn exec:java "-Dexec.mainClass=com.matchingengine.benchmark.OrderBookBenchmark"
```
# JMH version: 1.37
# VM version: JDK 17.0.8, Java HotSpot(TM) 64-Bit Server VM
# Warmup: 3 iterations, 1 s each
# Measurement: 5 iterations, 1 s each
# Threads: 1 thread, mode: Throughput

Benchmark                                Mode  Cnt        Score       Error  Units
OrderBookBenchmark.benchmarkOrderMatching thrpt    5  1845210.431 ± 12401.123  ops/s

## 🛠 Tech Stack

* **Language**: Java 17 (LTS)
* **Concurrency**: `java.util.concurrent`, `AtomicLong`, Atomic Mutexes, `Thread.onSpinWait()`
* **Testing**: JUnit 5, Awaitility
* **Benchmarking**: Java Microbenchmark Harness (JMH)
* **Build System**: Apache Maven

---

### Developed  by
**PIYUSH KUMAR**

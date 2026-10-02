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

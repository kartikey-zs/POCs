
---

# Dataset & Index Setup

- Dataset: **SIFT**
- Vectors: **1,000,000**
- Queries: **10,000**
- Index Type: **HNSW (JVector)**
- Parameters:
    - `M = 16`
    - `efConstruction = 100`
    - `efSearch = 200`

---

# Test 1: Initial Build + Query

## Build

- Build Time: **95.14s**
- Build Time (ms): **95519 ms**
- Build Memory: **1435 MB**
- Index Size: **1,000,000 vectors**

## Query Performance

- P50 Latency: **724.083 μs**
- Throughput: **1436.37 QPS**
- Recall@10: **0.9829**

---

# Test 2: Delete Performance

Deletes performed in batches of **100,000 vectors**.

---

## 10% Deleted (100,000 vectors)

- Total Time: **20 ms**
- Throughput: **5,000,000 deletes/sec**
- Index Size: **900,000**
- Recall@10 (before cleanup): **0.9840**
- Cleanup Time: **23972 ms**
- Heap Before: **2636 MB**
- Heap After: **1487 MB**
- Recall@10 (after cleanup): **0.9825**

---

## 20% Deleted (200,000 vectors)

- Total Time: **15 ms**
- Throughput: **6,666,666 deletes/sec**
- Index Size: **800,000**
- Recall@10 (before cleanup): **0.9844**
- Cleanup Time: **29133 ms**
- Heap Before: **2807 MB**
- Heap After: **1522 MB**
- Recall@10 (after cleanup): **0.9830**

---

## 30% Deleted (300,000 vectors)

- Total Time: **8 ms**
- Throughput: **12,500,000 deletes/sec**
- Index Size: **700,000**
- Recall@10 (before cleanup): **0.9848**
- Cleanup Time: **25603 ms**
- Heap Before: **2432 MB**
- Heap After: **1491 MB**
- Recall@10 (after cleanup): **0.9838**

---

## 40% Deleted (400,000 vectors)

- Total Time: **8 ms**
- Throughput: **12,500,000 deletes/sec**
- Index Size: **600,000**
- Recall@10 (before cleanup): **0.9854**
- Cleanup Time: **23637 ms**
- Heap Before: **2835 MB**
- Heap After: **1472 MB**
- Recall@10 (after cleanup): **0.9844**

---

## 50% Deleted (500,000 vectors)

- Total Time: **6 ms**
- Throughput: **16,666,666 deletes/sec**
- Index Size: **500,000**
- Recall@10 (before cleanup): **0.9860**
- Cleanup Time: **20501 ms**
- Heap Before: **2104 MB**
- Heap After: **1436 MB**
- Recall@10 (after cleanup): **0.9852**

---

## 60% Deleted (600,000 vectors)

- Total Time: **7 ms**
- Throughput: **14,285,714 deletes/sec**
- Index Size: **400,000**
- Recall@10 (before cleanup): **0.9847**
- Cleanup Time: **23285 ms**
- Heap Before: **1934 MB**
- Heap After: **1427 MB**
- Recall@10 (after cleanup): **0.9843**

---

## 70% Deleted (700,000 vectors)

- Total Time: **8 ms**
- Throughput: **12,500,000 deletes/sec**
- Index Size: **300,000**
- Recall@10 (before cleanup): **0.9811**
- Cleanup Time: **19376 ms**
- Heap Before: **2003 MB**
- Heap After: **1390 MB**
- Recall@10 (after cleanup): **0.9807**

---

## 80% Deleted (800,000 vectors)

- Total Time: **7 ms**
- Throughput: **14,285,714 deletes/sec**
- Index Size: **200,000**
- Recall@10 (before cleanup): **0.9493**
- Cleanup Time: **14286 ms**
- Heap Before: **2760 MB**
- Heap After: **1351 MB**
- Recall@10 (after cleanup): **0.9494**

---

## 90% Deleted (900,000 vectors)

- Total Time: **7 ms**
- Throughput: **14,285,714 deletes/sec**
- Index Size: **100,000**
- Recall@10 (before cleanup): **0.7596**
- Cleanup Time: **7922 ms**
- Heap Before: **1615 MB**
- Heap After: **1307 MB**
- Recall@10 (after cleanup): **0.7616**

---
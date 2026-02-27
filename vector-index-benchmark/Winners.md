## 🏆 CLEAR WINNER (HNSW : Tested for JVector)

**Configuration**
- **M** = 16
- **efConstruction** = 200
- **efSearch** = 200
- **Threads** = 8

### Why it wins
- **Best insert speedup**: **6.00×** (600 ms) — fastest parallel inserts with strong recall
- **Excellent concurrent search**: **10,900 QPS** — 2nd highest while keeping quality intact
- **Great recall**: **0.9860** — production-grade accuracy
- **Reasonable build time**: **5216 ms** — not prohibitively slow
- **Overall**: Best all-around balance of **speed, throughput, and quality**

---

## 🥈 Runners-up (depending on priorities)

### 🔍 Maximum Quality

**Configuration**
- **M** = 32
- **efConstruction** = 200
- **efSearch** = 200
- **Threads** = 12

**Results**
- **Recall**: **0.9995**
- **Concurrent search**: **6845 QPS**
- **Insert time**: **794 ms**
- **Build time**: **7908 ms**

**When to choose**
- Accuracy is critical
- Slower builds are acceptable

---

### ⚡ Maximum Throughput (lower recall acceptable)

**Configuration**
- **M** = 16
- **efConstruction** = 200
- **efSearch** = 100
- **Threads** = 12

**Results**
- **Concurrent search**: **10,582 QPS**
- **Recall**: **0.9446**
- **Insert time**: **983 ms**

**When to choose**
- Throughput is the top priority
- ~94% recall is acceptable
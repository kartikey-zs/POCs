# Key Architectural Findings for GridGain RFC

---

## Finding 1 — Persist the Index to Disk

Load time (~67ms) vs full rebuild (~46–95s) represents a **~700–1400× improvement in recovery time**.  
The cost of persistence infrastructure is therefore fully justified.

On node restart, GridGain should **load the persisted index file** rather than rebuilding the index from table data.

---

## Finding 2 — Java 22+ Is a Hard Requirement for On-Disk Vector Search

On **Java 20/21**, on-disk latency is **3.5–4× worse than on-heap**.

On **Java 22**, on-disk latency **matches on-heap performance**.

The `MemorySegmentReader` activated on Java 22+ is **not a nice-to-have feature** — it is what makes the on-disk approach viable.

GridGain 9's vector search feature should therefore either:

- **Declare Java 22 as the minimum runtime requirement**, or
- Clearly **document the latency penalty** when running on lower Java versions.

---

## Finding 3 — Do Not Implement Checkpoint-Resume

JVector's:

- `OnHeapGraphIndex.save()`
- `GraphIndexBuilder.load()`

APIs are marked **`@Deprecated` and `@Experimental`** and consistently perform **2–3× worse than a full rebuild**.

GridGain should **not build recovery infrastructure around this API**.

The correct recovery flow should be:

1. **Load persisted index file** if it exists.
2. Otherwise **rebuild the index from table data**.

---

## Finding 4 — Memory Savings Enable Significantly Denser Node Packing

On-disk mode uses **~19MB JVM heap** compared to **~1435MB for on-heap indexing**.

This represents a **~98.7% reduction in heap usage**.

In practical cluster terms:

- A node with **32GB heap** could host roughly **~1680 on-disk partitions** vs **~22 on-heap partitions** (approximate estimate).
- Alternatively, the **same number of partitions could run on much cheaper hardware**.

This has a **direct impact on GridGain cluster sizing and infrastructure cost**.

---

## Finding 5 — PQ Is Critical for On-Disk Search Performance (Not Wired in This POC)

This POC used **`INLINE_VECTORS` with exact scoring**, meaning the search algorithm **reads full float vectors from disk for every candidate** during traversal.

Product Quantization (**PQ**) was persisted but **not integrated into the search path**.

In production systems, the expected design should be:

1. **Use PQ for L0 traversal scoring**
    - In-memory byte-code lookups instead of disk reads per candidate.

2. **Perform exact reranking only for the final Top-K results**.

This would significantly **reduce disk I/O during search traversal**, potentially improving latency beyond what was observed in this POC.

---

## Suggestions
1. Wire PQ into search traversal 
   1. Currently, the POC uses INLINE_VECTORS with exact scoring for every candidate during L0 traversal
   2. this means a disk read per candidate
   3. In production, PQ codes should be used for the traversal scoring pass (approximate, in-memory)
   4. exact scoring should be used only for the final top-K re-ranking 
   5. This would reduce disk I/O during traversal from ~300 reads per query down to ~10 (only the final rerank candidates).
   6. Expected impact: lower steady-state latency and significantly lower cold-start latency.
   7. Recommended approach: write index with FUSED_ADC feature
2. Test with higher-dimensional dataset
# JVector Configuration Reference

## 1. Core Build Parameters (GraphIndexBuilder)

### M (Graph Degree)
- **What**: Number of bidirectional connections per node in the graph
- **Tunable**: YES (key parameter)
- **Effect**: Higher M = more connections = better recall, slower build, more memory
- **Typical values**: 8–32
    - 8 → speed
    - 16 → balanced
    - 32 → accuracy

---

### efConstruction (Build Beam Width)
- **What**: Size of candidate pool when selecting neighbors during graph construction
- **Tunable**: YES (secondary parameter)
- **Effect**: Higher efConstruction improves graph quality but slows build
- **Typical values**: 100–200
    - Diminishing returns beyond 200

---

### efSearch (Search Beam Width)
- **What**: Number of candidates maintained during search traversal
- **Tunable**: YES (most important search parameter)
- **Effect**: Higher efSearch = higher recall, slower queries
- **Typical values**: 10–400
    - 10 → very fast
    - 100 → balanced
    - 400 → high accuracy

---

### neighborOverflow (default = 1.2)
- **What**: Temporary over-connection multiplier during construction (M × overflow)
- **Tunable**: Rarely
- **Effect**: Higher = better graph quality, slower build
- **Typical values**: 1.2–1.5
    - Default (1.2) is usually optimal

---

### alpha (default = 1.2)
- **What**: Diversity vs distance tradeoff when selecting neighbors (DiskANN parameter)
- **Tunable**: Rarely
- **Effect**: Higher favors diverse neighbors, improves long-range navigation
- **Typical values**: 1.0–1.5

---

### addHierarchy (true / false)
- **What**: Enable multi-layer hierarchical graph (HNSW) vs single-layer NSW
- **Tunable**: Usually no
- **Effect**:
    - true → HNSW (fast for large datasets)
    - false → NSW (simpler, slower)
- **Typical values**: true
    - Always recommended for datasets > 10K

---

### refineFinalGraph (true / false)
- **What**: Cleanup pass after construction to improve edge quality
- **Tunable**: YES
- **Effect**: Better recall at cost of longer build time
- **Typical values**: true

---

## 2. Search Configuration

### SearchScoreProvider (SSP)
- **What**: Defines how distances are computed during search
- **Tunable**: YES
- **Types**:
    - `DefaultSearchScoreProvider.exact()` → exact distances (used in benchmarks)
    - Approximate + reranking → faster, slightly lower accuracy
- **Effect**:
    - Exact → slower, perfect accuracy
    - Approximate → faster, some recall loss

---

### Bits.ALL (acceptOrds)
- **What**: Bitmap specifying which vector IDs are eligible for results
- **Tunable**: YES (for filtering)
- **Effect**:
    - `Bits.ALL` → no filtering
    - Custom Bits → metadata filtering
- **Example use case**:
    - "Find similar products that are in stock"

---

### topK
- **What**: Number of nearest neighbors to return
- **Tunable**: YES (per query)
- **Effect**: Higher K = slightly slower queries
- **Typical values**: 10–100

---

### rerankK (Approximate Search Only)
- **What**: Number of approximate candidates fetched before exact reranking
- **Tunable**: YES
- **Effect**: Higher = better recall, slower queries
- **Typical values**: `2 × topK`

---

## 3. Distance / Similarity Function

### VectorSimilarityFunction
- **What**: Distance metric used for scoring
- **Tunable**: YES (must match embeddings)
- **Options**:
    - `EUCLIDEAN` → L2 distance (SIFT, vision embeddings)
    - `DOT_PRODUCT` → inner product
    - `COSINE` → cosine similarity (normalized text embeddings)
- **Important**: Must match your embedding space

---

## 4. Advanced / Production Parameters

### BuildScoreProvider (BSP)
- **What**: How vectors are scored during graph construction
- **Tunable**: Advanced use
- **Types**:
    - `randomAccessScoreProvider()` → exact distances (default)
    - `pqBuildScoreProvider()` → compressed vectors during build
- **Effect**:
    - PQ build → much faster, slightly lower quality

---

### SIMD / Parallel Executors
- **What**: Thread pools for parallel execution
- **Tunable**: YES
- **Options**:
    - `PhysicalCoreExecutor.pool()` → SIMD optimized
    - `ForkJoinPool.commonPool()` → general parallelism
- **Typical usage**: Defaults are sufficient

---

### Concurrent Graph Updates
- **What**: Thread-safe concurrent insertions
- **Tunable**: Automatic (JVector 4.x)
- **Effect**: Efficient parallel builds
- **Note**: No manual configuration required

---

## 5. Compression / Quantization (Advanced)

### Product Quantization (PQ)
- **What**: Compress vectors 8–32× for memory efficiency
- **Tunable**: YES
- **Parameters**:
    - `nSubspaces` (e.g., 16)
    - `nCentroids` (e.g., 256)
- **Effect**:
    - 128 dims × 4 bytes = 512 bytes
    - Compressed to ~16 bytes (32×)
- **Use when**: Dataset > 10M or memory constrained

---

### NVQ (Normalized Vector Quantization)
- **What**: Compression optimized for normalized vectors
- **Tunable**: YES
- **Effect**: Better suited for cosine similarity
- **Use when**: Using COSINE distance

---

## 6. Rarely Changed Parameters

### maxDegrees
- **What**: Per-layer M values in hierarchical graph
- **Tunable**: Advanced only
- **Typical**: Single value for all layers

---

### Internal Graph View Settings
- **What**: Read-only graph views for concurrent reads
- **Tunable**: NO
- **Effect**: Automatically managed

---

### NodeQueue Sizes
- **What**: Internal candidate pools during search
- **Tunable**: NO
- **Effect**: Auto-sized based on efSearch

---

## Quick Reference Table

| Parameter                | Typical Value | Recall Impact | Speed Impact | When to Tune |
|--------------------------|---------------|---------------|--------------|--------------|
| M                        | 16            | Medium        | Medium       | Common       |
| efConstruction           | 100–200       | Low           | Build only   | Sometimes    |
| efSearch                 | 100           | HIGH          | HIGH         | Every query  |
| neighborOverflow         | 1.2           | Low           | Build only   | Rare         |
| alpha                    | 1.2           | Low           | Minimal      | Rare         |
| addHierarchy             | true          | HIGH          | HIGH         | Never        |
| refineFinalGraph         | true          | Low           | Build only   | Sometimes    |
| VectorSimilarityFunction | L2 / Cosine   | N/A           | N/A          | Per dataset  |
| Bits.ALL                 | ALL           | N/A           | Minimal      | Filtering    |
| PQ Compression           | OFF           | Medium        | HIGH         | At scale     |

---

## Tuning Strategy

### Learning / Prototyping
```java
new GraphIndexBuilder(
    bsp,
    dimension,
    16,      // M
    100,     // efConstruction
    1.2f,    // neighborOverflow
    1.2f,    // alpha
    true,    // addHierarchy
    true     // refineFinalGraph
);
```

### Production – High Throughput
```java
M = 8;
efConstruction = 100;
efSearch = 50;
```

### Production – High Accuracy
```java
M = 32;
efConstruction = 200;
efSearch = 400;
```

### Memory Constrained
```java
M = 8;
Enable PQ compression;
```
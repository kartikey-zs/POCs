# **Vector Index: Theory & Design**

## Overview

This document explains the **concepts, algorithms, trade-offs, and decision logic** behind vector indexing methods used in this project. It is intended to help engineers understand _why_ and _when_ to use each index type.

***


## Supported Index Types

### 1. Flat (Brute-force) Index

- Computes distance against **every vector**
- Guarantees **exact results (100% recall)**
- Time complexity: **O(n)** per query

**When it makes sense**
- Very small datasets (<1K vectors)
- Very low query volume
- Validation or correctness testing
***


### 2. HNSW (Hierarchical Navigable Small World)

**How it works:**
- Builds a multi-layer graph where each node is a vector
- Upper layers: sparse, long-range connections (highway)
- Lower layers: dense, short-range connections (local streets)
- Search: navigate from top layer down, getting progressively closer

**Why it's fast:**
- Logarithmic search complexity in practice
- Skips irrelevant regions via hierarchical structure
- Greedy graph traversal with beam search


#### Core Parameters

##### **M — Graph Connectivity**

- Controls number of neighbors per node
- Higher `M` → better recall, more memory, slower build

##### **efConstruction — Build Quality**

- Controls graph construction accuracy
- Higher values → longer build, marginal recall improvement

##### **efSearch — Query Accuracy vs Speed**

- Controls how much of the graph is explored at query time
- **Most important parameter**
***


### 3. IVF (Inverted File Index)

**How it works:**
1. **Build:** K-means clusters vectors into nList groups
2. **Search:** Find nProbe nearest centroids, search only those clusters
3. **Tradeoff:** Speed (search subset) vs Accuracy (might miss neighbors in other clusters)

**Two-phase search:**
- Coarse: Compare query to all centroids (fast, nList comparisons)
- Fine: Brute force within nProbe clusters (slower, but only subset)

**Key Parameters**

- `nList`: number of clusters
- `nProbe`: number of clusters searched per query
***


## Distance Metrics

**Euclidean (L2):**
- Measures absolute distance in space
- Use for: SIFT descriptors, general-purpose embeddings
- Formula: sqrt(Σ(v1[i] - v2[i])²)

**Cosine Similarity:**
- Measures angle between vectors (direction, not magnitude)
- Use for: Text embeddings, normalized vectors
- Formula: (v1 · v2) / (||v1|| × ||v2||)

***


## Recall vs Latency Trade-off

| Index | Recall  | Latency   | Predictability |
|-------|---------|-----------|----------------|
| Flat  | 100%    | Slow      | Deterministic  |
| HNSW  | 99–100% | Very Fast | Tunable        |
| IVF   | 96–99%  | Fast      | Coarse         |

***


## When to Use What?

### ❓ When is FLAT better?

FLAT is faster when:

    (total_queries × query_time) < index_build_time

Typical cases:

- Validation workloads

- Very infrequent searches

- Small datasets

***


### ❓ When does HNSW win?

- High query volume

- Real-time latency requirements

- Search, recommendation, RAG systems

HNSW amortizes build cost quickly and delivers **sub-millisecond queries**.

## Recommended Parameters by Scale

| Dataset Size | M  | efConstruction | efSearch |
|--------------|----|----------------|----------|
| 10K          | 8  | 100            | 100      |
| 100K         | 12 | 100            | 150–200  |
| 1M           | 16 | 100            | 200–400  |
| 10M          | 24 | 200            | 500+     |

***


### ❓ When does IVF make sense?

- Batch processing

- GPU acceleration

- Offline analytics

- Teams preferring algorithmic simplicity

IVF is easier to debug and parallelize, especially on GPUs.

***

## Production Considerations

**Memory:**
- Flat: ~dimensions × vectors × 4 bytes (minimal)
- HNSW: 2-3x Flat (stores graph structure)
- IVF: Similar to Flat (cluster metadata is small)

**Build Time:**
- Flat: Instant (just load vectors)
- HNSW: Minutes for millions (graph construction)
- IVF: Depends on k-means iterations (20 iterations = slow)

**Index Maintenance:**
- Flat: Easy (append/delete instantly)
- HNSW: Mark deleted, periodic cleanup needed
- IVF: Cluster imbalance after modifications → rebuild needed

***
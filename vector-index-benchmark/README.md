# Vector Index Benchmark

> Comprehensive benchmarking of vector search algorithms (Flat, HNSW, IVF) on real datasets with production insights.

---

## Documentation

| Document                         | Description                                            |
|----------------------------------|--------------------------------------------------------|
| **[Theory.md](Theory.md)**       | Algorithm concepts, parameters, and decision framework |
| **[Benchmark.md](Benchmark.md)** | Complete benchmark results, analysis, and comparisons  |
| **[JVector.md](JVector.md)**     | JVector-specific configuration and usage guide         |

---

## Key Findings

**TL;DR:** HNSW delivers the best performance for production workloads.

| Index              | Build Time | Query Latency (P50) | Throughput | Recall | Best For                |
|--------------------|------------|---------------------|------------|--------|-------------------------|
| **Flat**           | Instant    | 1,026 μs            | 952 QPS    | 100%   | <1K vectors, validation |
| **IVF**            | 651 ms     | 206 μs              | 4,762 QPS  | 96%    | Batch processing, GPU   |
| **HNSW (jelmerk)** | 1,211 ms   | 103.8 μs            | 10,000 QPS | 99.8%  | Production standard     |
| **HNSW (JVector)** | 689 ms     | 84.6 μs             | 12,500 QPS | 99.4%  | **Best overall**        |

### Why JVector Wins

- **1.76x faster build** than jelmerk (689ms vs 1,211ms)
- **22% faster queries** (84.6μs vs 103.8μs)
- **SIMD optimization** via Panama Vector API
- **Native deletion support** (no external tools needed)
- **Pure Java** (no JNI complexity)

*See [Benchmark.md](Benchmark.md) for detailed analysis.*

---

## Quick Start

### Prerequisites

- **Java 20+** (for SIMD support via `--add-modules jdk.incubator.vector`)
- **Gradle** (wrapper included)
- **16GB+ RAM** recommended for larger datasets (though i wasn't able to do a test on 1M dataset with 24gb ram-12 cores)

### 1. Clone the Repository
```bash
git clone git@github.com:Kartikk1127/vector-index-benchmark.git
cd vector-index-benchmark
```

### 2. Download SIFT Dataset

Download the SIFT 10K dataset from [ANN Benchmarks](http://corpus-texmex.irisa.fr/):
```bash
# Create dataset directory
mkdir -p datasets/sift

# Download files
wget http://corpus-texmex.irisa.fr/texmex/siftsmall.tar.gz
tar -xzf siftsmall.tar.gz -C datasets/sift/

# You should now have:
# datasets/sift/siftsmall_base.fvecs
# datasets/sift/siftsmall_query.fvecs
# datasets/sift/siftsmall_groundtruth.ivecs
```

### 3. Update Dataset Path

Edit `src/main/java/SiftMain.java.java`:
```java
// Update this line with your path
List<Vector> indexVectors = DatasetLoader.loadFVectors("path/to/siftsmall_base.fvecs");
List<Vector> queryVectors = DatasetLoader.loadFVectors("path/to/siftsmall_query.fvecs");
List<int []> groundTruthBig = DatasetLoader.loadIVecs("path/to/siftsmall_groundtruth.ivecs");
```

### 4. Run Benchmarks (Make sure Java version >= 20 to get the SIMD optimization)
```bash
# Build the project
./gradlew build

# Run with SIMD support (recommended)
./gradlew run --args="--add-modules jdk.incubator.vector"

# Or run without SIMD
./gradlew run
```

### 5. Expected Output
```
Loading SIFT dataset...
Loaded 10000 base vectors
Loaded 100 query vectors

=== Benchmarking Flat Index ===
Build Time: 213 ms
P50 Latency: 1,026 μs
Recall: 100%

=== Benchmarking HNSW (JVector) ===
Build Time: 689 ms
P50 Latency: 84.6 μs
Recall: 99.4%

...
```

---

## Project Structure
```
vector-index-benchmark/
├── src/main/java/
│   ├── core/              # Core vector & distance logic
│   │   ├── Vector.java
│   │   ├── DistanceMetric.java
│   │   └── QueryResult.java
│   ├── index/             # Index implementations
│   │   ├── VectorIndex.java
│   │   ├── FlatIndex.java
│   │   ├── HNSWIndex.java
│   │   ├── JVectorHNSWIndex.java
│   │   └── IVFIndex.java
│   ├── benchmark/         # Benchmarking framework
│   │   └── BenchmarkRunner.java
│   └── utils/             # Dataset loading & utilities
├── Theory.md              # Algorithm theory & design
├── Benchmark.md           # Complete benchmark results
├── JVector.md            # JVector configuration guide
└── README.md             # This file
```

---

## Implemented Algorithms

### 1. Flat Index (Brute Force)
- **O(n)** exact search
- 100% recall guarantee
- Baseline for correctness validation

### 2. HNSW (Hierarchical Navigable Small World)
- Graph-based approximate search
- Logarithmic complexity in practice
- Best for high-QPS production systems
- **Implementations:** jelmerk library + JVector

### 3. IVF (Inverted File Index)
- K-means clustering + two-phase search
- Simpler than HNSW (easier to debug)

*Read [Theory.md](Theory.md) for detailed explanations.*

---

## Benchmark Features

- **Multiple distance metrics:** Euclidean (L2), Cosine similarity
- **Comprehensive metrics:** Build time, memory, latency percentiles (P50/P95/P99), throughput (QPS)
- **Ground-truth validation:** Recall measured against SIFT ground truth
- **Real datasets:** SIFT 10K (ANN Benchmarks format)
- **Systematic parameter tuning:** Tested 10+ configurations per algorithm

---

## Decision Guide

### When to use Flat?

**Use when:** `(total_queries × query_time) < build_time`

- Very small datasets (<1K vectors)
- Infrequent searches (validation, testing)
- 100% recall required

### When to use HNSW?

- High query volume (1000s+ queries)
- Real-time latency requirements (<1ms)
- Production systems (search, recommendations, RAG)
- **Optimal config for 10K:** M=8, efConstruction=100, efSearch=100

### When to use IVF?

- Batch processing (offline analytics)
- Teams preferring simpler algorithms (k-means vs graphs)
- **Optimal config for 10K:** nList=50, nProbe=5

*See [Theory.md](Theory.md) for complete decision framework.*

---

## Test Environment

- **Machine:** MacBook Pro
- **CPU:** Apple Silicon / Intel (SIMD-capable)
- **Memory:** 24 GB RAM
- **Storage:** 512 GB SSD
- **Java:** OpenJDK 20+ with Panama Vector API

---

## Performance Highlights

### Build Time Comparison
```
Flat:    Instant (just load vectors)
IVF:     651ms   (k-means clustering)
JVector: 689ms   (graph construction)
jelmerk: 1,211ms (graph construction)
```

### Query Latency Comparison
```
Flat:    1,026 μs  (O(n) exhaustive search)
IVF:     206 μs    (coarse + fine search)
jelmerk: 103.8 μs  (graph navigation)
JVector: 84.6 μs   (SIMD-optimized navigation) ⚡
```

### Distance Calculations (10K dataset)
```
Flat:    10,000  (exhaustive)
HNSW:    639     (16x reduction!)
IVF:     1,209   (depends on nProbe)
```

---

## Customization

### Run Your Own Dataset

Implement the dataset loader in `utils/DatasetLoader.java`:
```java
public static List<Vector> loadCustomVectors(String path) {
    // Your loading logic
}
```

### Add New Index Implementation

1. Implement `VectorIndex` interface
2. Add to `Main.java`
3. Run benchmarks

### Tune Parameters

Edit configurations in `Main.java`:
```java
// HNSW
VectorIndex hnsw = new JVectorHNSWIndex(
    16,   // M - graph connectivity
    200,  // efConstruction - build quality
    100,  // efSearch - query accuracy
    DistanceMetric.EUCLIDEAN
);

// IVF
VectorIndex ivf = new IVFIndex(
    100,  // nList - number of clusters
    10    // nProbe - clusters to search
);
```

---

## Contributing

Contributions welcome! Areas of interest:

- [ ] Additional algorithms (LSH, ScaNN, DiskANN)
- [ ] Product Quantization (PQ) compression
- [ ] Distributed / sharded indexes
- [ ] More datasets (GloVe, LAION, etc.)
- [ ] GPU acceleration benchmarks

---

## License

MIT License - see [LICENSE](LICENSE) file for details.

---

## Acknowledgments

- **Datasets:** [ANN Benchmarks](http://ann-benchmarks.com/)
- **Libraries:** [JVector](https://github.com/jbellis/jvector), [hnswlib-jna](https://github.com/jelmerk/hnswlib)
- **Inspiration:** FAISS, Annoy, Qdrant benchmarking methodologies

---

## Contact

**Kartikey Srivastava**

- GitHub: [@Kartikk1127](https://github.com/Kartikk1127)
- LinkedIn: [Connect with me](https://www.linkedin.com/in/kartikey-srivastava-bb913423a/)

*Built while investigating vector search for GridGain Systems*

---

**⭐ If you find this useful, please star the repository!**
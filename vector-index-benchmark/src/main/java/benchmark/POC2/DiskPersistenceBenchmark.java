package benchmark.POC2;

import io.github.jbellis.jvector.disk.*;
import io.github.jbellis.jvector.graph.*;
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndex;
import io.github.jbellis.jvector.graph.similarity.BuildScoreProvider;
import io.github.jbellis.jvector.quantization.CompressedVectors;
import io.github.jbellis.jvector.quantization.ImmutablePQVectors;
import io.github.jbellis.jvector.quantization.ProductQuantization;
import io.github.jbellis.jvector.quantization.PQVectors;
import io.github.jbellis.jvector.util.Bits;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.*;
import java.util.*;

public class DiskPersistenceBenchmark {

    private static final VectorTypeSupport vts = VectorizationProvider.getInstance().getVectorTypeSupport();

    // =====================
    // Result Records
    // =====================

    public record BuildResult(
            OnHeapGraphIndex graph,
            List<VectorFloat<?>> jvectorVectors,
            CompressedVectors pqVectors,
            long buildTimeMs,
            long pqTrainTimeMs,
            long pqEncodeTimeMs
    ) {}

    public record WriteResult(
            long graphWriteTimeMs,
            long graphFileSizeBytes,
            long pqWriteTimeMs,
            long pqFileSizeBytes
    ) {}

    public record LoadResult(
            OnDiskGraphIndex index,
            CompressedVectors pqVectors,
            ReaderSupplier readerSupplier,
            long loadTimeMs
    ) {}

    public record ColdStartResult(double firstQueryMicros) {}

    public record SteadyStateResult(
            double p50Micros,
            double p95Micros,
            double p99Micros,
            double avgRecall
    ) {}

    public record MemoryResult(
            long onDiskIndexBytes,
            long pqVectorsBytes,
            long totalOnDiskModeBytes,
            long onHeapBytes
    ) {}

    public record CheckpointResult(
            long saveTimeMs,
            long resumeAndFinishTimeMs,
            long fullRebuildTimeMs
    ) {}

    // =====================
    // Phase 1: Build
    // =====================

    /**
     * Converts raw vectors to JVector format, builds the graph, trains PQ, and encodes all vectors.
     */
    public static BuildResult buildIndex(
            List<core.Vector> vectors,
            int dimension,
            int m,
            int efConstruction,
            int pqSubspaces
    ) {
        // Convert to JVector VectorFloat format
        List<VectorFloat<?>> jvectorVectors = new ArrayList<>(vectors.size());
        for (core.Vector v : vectors) {
            VectorFloat<?> vf = vts.createFloatVector(v.dimensions());
            for (int i = 0; i < v.dimensions(); i++) {
                vf.set(i, v.vector()[i]);
            }
            jvectorVectors.add(vf);
        }

        var ravv = new ListRandomAccessVectorValues(jvectorVectors, dimension);
        var bsp = BuildScoreProvider.randomAccessScoreProvider(ravv, VectorSimilarityFunction.EUCLIDEAN);

        // Build graph
        long buildStart = System.currentTimeMillis();
        var builder = new GraphIndexBuilder(bsp, dimension, m, efConstruction, 1.2f, 1.2f, true, false);
        builder.build(ravv);
        long buildTimeMs = System.currentTimeMillis() - buildStart;

        // Train PQ codebook
        long pqTrainStart = System.currentTimeMillis();
        var pq = ProductQuantization.compute(ravv, pqSubspaces, 256, false);
        long pqTrainTimeMs = System.currentTimeMillis() - pqTrainStart;

        // Encode all vectors using trained codebook
        long pqEncodeStart = System.currentTimeMillis();
        var pqVectors = pq.encodeAll(ravv);
        long pqEncodeTimeMs = System.currentTimeMillis() - pqEncodeStart;

        return new BuildResult(
                (OnHeapGraphIndex) builder.getGraph(),
                jvectorVectors,
                pqVectors,
                buildTimeMs,
                pqTrainTimeMs,
                pqEncodeTimeMs
        );
    }

    // =====================
    // Phase 2: Write
    // =====================

    /**
     * Writes the graph (with inline vectors) and PQ vectors to separate files.
     * Measures write time and records file sizes.
     */
    public static WriteResult writeIndex(
            OnHeapGraphIndex graph,
            List<VectorFloat<?>> jvectorVectors,
            int dimension,
            CompressedVectors pqVectors,
            Path graphPath,
            Path pqPath
    ) throws IOException {
        var ravv = new ListRandomAccessVectorValues(jvectorVectors, dimension);

        // Write graph with INLINE_VECTORS feature
        long graphWriteStart = System.currentTimeMillis();
        OnDiskGraphIndex.write(graph, ravv, graphPath);
        long graphWriteTimeMs = System.currentTimeMillis() - graphWriteStart;
        long graphFileSizeBytes = Files.size(graphPath);

        // Write PQ vectors to a separate file
        long pqWriteStart = System.currentTimeMillis();
        try (var out = new SimpleWriter(pqPath)) {
            pqVectors.write(out);
        }
        long pqWriteTimeMs = System.currentTimeMillis() - pqWriteStart;
        long pqFileSizeBytes = Files.size(pqPath);

        return new WriteResult(graphWriteTimeMs, graphFileSizeBytes, pqWriteTimeMs, pqFileSizeBytes);
    }

    // =====================
    // Phase 3: Load
    // =====================

    /**
     * Loads the on-disk graph and PQ vectors from file.
     * Note: caller is responsible for closing LoadResult.readerSupplier() when done.
     */
    public static LoadResult loadIndex(Path graphPath, Path pqPath) throws IOException {
        long loadStart = System.currentTimeMillis();

        // Load graph — ReaderSupplier stays open as long as index is in use
        var readerSupplier = ReaderSupplierFactory.open(graphPath);
        var index = OnDiskGraphIndex.load(readerSupplier);

        // Load PQ vectors
        CompressedVectors pqVectors;
        try (var supplier = ReaderSupplierFactory.open(pqPath);
             var reader = supplier.get()) {
            pqVectors = ImmutablePQVectors.load(reader);
        }

        long loadTimeMs = System.currentTimeMillis() - loadStart;
        return new LoadResult(index, pqVectors, readerSupplier, loadTimeMs);
    }

    // =====================
    // Phase 4: Cold Start
    // =====================

    /**
     * Measures the first query latency after loading — no warmup, cold OS page cache.
     */
    public static ColdStartResult measureColdStart(
            OnDiskGraphIndex index,
            VectorFloat<?> queryVector,
            int k,
            int efSearch
    ) throws IOException {
        long start = System.nanoTime();
        searchOnDisk(index, queryVector, k, efSearch);
        long end = System.nanoTime();
        return new ColdStartResult((end - start) / 1000.0);
    }

    // =====================
    // Phase 5: Steady State
    // =====================

    /**
     * Runs warmup queries to heat the OS page cache, then measures steady-state latency and recall.
     */
    public static SteadyStateResult measureSteadyState(
            OnDiskGraphIndex index,
            List<VectorFloat<?>> queryVectors,
            List<int[]> groundTruth,
            int k,
            int efSearch,
            int warmupCount
    ) throws IOException {
        // Warmup — cycles through query vectors if warmupCount > queryVectors.size()
        for (int i = 0; i < warmupCount; i++) {
            searchOnDisk(index, queryVectors.get(i % queryVectors.size()), k, efSearch);
        }

        // Measure latency and recall across all query vectors
        List<Long> latencies = new ArrayList<>(queryVectors.size());
        double totalRecall = 0.0;

        for (int i = 0; i < queryVectors.size(); i++) {
            long start = System.nanoTime();
            var result = searchOnDisk(index, queryVectors.get(i), k, efSearch);
            long end = System.nanoTime();

            latencies.add(end - start);
            totalRecall += calculateRecall(result, groundTruth.get(i), k);
        }

        Collections.sort(latencies);
        double p50 = latencies.get(latencies.size() / 2) / 1000.0;
        double p95 = latencies.get((int) (latencies.size() * 0.95)) / 1000.0;
        double p99 = latencies.get((int) (latencies.size() * 0.99)) / 1000.0;
        double avgRecall = totalRecall / queryVectors.size();

        return new SteadyStateResult(p50, p95, p99, avgRecall);
    }

    // =====================
    // Phase 6: Memory
    // =====================

    /**
     * Compares heap usage of on-disk mode (OnDiskGraphIndex + PQVectors) vs full on-heap mode.
     * Uses ramBytesUsed() from the Accountable interface on both index types.
     */
    public static MemoryResult measureMemory(
            OnDiskGraphIndex onDisk,
            CompressedVectors pqVectors,
            OnHeapGraphIndex onHeap,
            VectorFloat<?> queryVector,
            int k,
            int efSearch
    ) throws Exception {
//        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
//
//        System.gc();
//        Thread.sleep(200);
//        long beforeOnDisk = memBean.getHeapMemoryUsage().getUsed();
//
//        searchOnDisk(onDisk, queryVector, k, efSearch);
//
//        System.gc();
//        Thread.sleep(200);
//        long afterOnDisk = memBean.getHeapMemoryUsage().getUsed();
//        long onDiskBytes = Math.max(0, afterOnDisk - beforeOnDisk);
        long onDiskBytes = onDisk.ramBytesUsed();

        // PQ ramBytesUsed() is accurate — it's just a byte array, no lazy init
        long pqBytes = pqVectors.ramBytesUsed();

        // OnHeap ramBytesUsed() is also accurate
        long onHeapBytes = onHeap.ramBytesUsed();

        return new MemoryResult(onDiskBytes, pqBytes, onDiskBytes + pqBytes, onHeapBytes);
    }

    // =====================
    // Phase 7: Checkpoint-Resume
    // =====================

    /**
     * Benchmarks the checkpoint-resume path:
     * 1. Builds index with first 50% of vectors
     * 2. Saves checkpoint via OnHeapGraphIndex.save()
     * 3. Loads checkpoint and continues adding remaining 50%
     * Compares total resume time against a full rebuild from scratch.
     */
    public static CheckpointResult benchmarkCheckpointResume(
            List<VectorFloat<?>> allVectors,
            int dimension,
            int m,
            int efConstruction,
            Path checkpointPath
    ) throws IOException {
        int half = allVectors.size() / 2;
        var ravvFull = new ListRandomAccessVectorValues(allVectors, dimension);
        var bsp = BuildScoreProvider.randomAccessScoreProvider(ravvFull, VectorSimilarityFunction.EUCLIDEAN);

        // ---- Build first half ----
        System.out.println("Building the first half");
        var builder = new GraphIndexBuilder(bsp, dimension, m, efConstruction, 1.2f, 1.2f, true, false);
        for (int i = 0; i < half; i++) {
            builder.addGraphNode(i, allVectors.get(i));
        }
        System.out.println("Built the first half. Now initiating cleanup");
        // cleanup() is required before save() — sets allMutationsCompleted = true
        builder.cleanup();
        System.out.println("Cleanup done. Initiating checkpoint");

        // ---- Save checkpoint ----
        long saveStart = System.nanoTime();
        try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(checkpointPath)))) {
            ((OnHeapGraphIndex) builder.getGraph()).save(out);
        }
        System.out.println("Checkpoint saved");
        long saveTimeMs = (System.nanoTime() - saveStart) / 1_000_000;

        // ---- Load checkpoint + continue adding second half ----
        long resumeStart = System.nanoTime();
        System.out.println("Continuing now.");
        var resumeBsp = BuildScoreProvider.randomAccessScoreProvider(ravvFull, VectorSimilarityFunction.EUCLIDEAN);
        var resumeBuilder = new GraphIndexBuilder(resumeBsp, dimension, m, efConstruction, 1.2f, 1.2f, true, false);
        System.out.println("Loading the checkpoint");
        try (var reader = new SimpleReader(checkpointPath)) {
            resumeBuilder.load(reader);
        }
        System.out.println("Checkpoint loaded");
        for (int i = half; i < allVectors.size(); i++) {
            resumeBuilder.addGraphNode(i, allVectors.get(i));
        }
        System.out.println("Adding further nodes to the index. Initiating cleanup again");
        resumeBuilder.cleanup();
        System.out.println("Cleanup done");
        long resumeAndFinishTimeMs = (System.nanoTime() - resumeStart) / 1_000_000;

        // ---- Full rebuild from scratch (baseline comparison) ----
        System.out.println("Doing full rebuild now");
        long fullRebuildStart = System.nanoTime();
        var fullBsp = BuildScoreProvider.randomAccessScoreProvider(ravvFull, VectorSimilarityFunction.EUCLIDEAN);
        var fullBuilder = new GraphIndexBuilder(fullBsp, dimension, m, efConstruction, 1.2f, 1.2f, true, false);
        fullBuilder.build(ravvFull);
        long fullRebuildTimeMs = (System.nanoTime() - fullRebuildStart) / 1_000_000;
        System.out.println("Rebuild done.");

        return new CheckpointResult(saveTimeMs, resumeAndFinishTimeMs, fullRebuildTimeMs);
    }

    // =====================
    // Internal Helpers
    // =====================

    /**
     * Searches the on-disk index using exact scoring from INLINE_VECTORS.
     * Opens a fresh view per query — safe for concurrent use, slight overhead per call.
     */
    static io.github.jbellis.jvector.graph.SearchResult searchOnDisk(
            OnDiskGraphIndex index,
            VectorFloat<?> queryVector,
            int k,
            int efSearch
    ) throws IOException {
        // View implements RandomAccessVectorValues — used here as the scoring source
        try (var view = index.getView()) {
            var bsp = BuildScoreProvider.randomAccessScoreProvider(view, VectorSimilarityFunction.EUCLIDEAN);
            var ssp = bsp.searchProviderFor(queryVector);
            try (var searcher = new GraphSearcher(index)) {
                return searcher.search(ssp, k, efSearch, 0.0f, 0.0f, Bits.ALL);
            }
        }
    }

    /**
     * Calculates recall@k by comparing result ordinals against SIFT ground truth ordinals.
     * Works directly with ordinals — no string prefix needed for SIFT.
     */
    static double calculateRecall(
            io.github.jbellis.jvector.graph.SearchResult result,
            int[] groundTruth,
            int k
    ) {
        Set<Integer> resultNodes = new HashSet<>();
        for (var ns : result.getNodes()) {
            resultNodes.add(ns.node);
        }
        int matches = 0;
        for (int i = 0; i < Math.min(k, groundTruth.length); i++) {
            if (resultNodes.contains(groundTruth[i])) matches++;
        }
        return (double) matches / k;
    }
}
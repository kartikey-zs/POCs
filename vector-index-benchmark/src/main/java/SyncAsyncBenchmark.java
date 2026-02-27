import benchmark.BenchmarkExecutors;
import benchmark.BenchmarkRunner;
import benchmark.Metrics;
import core.QueryResult;
import core.Vector;
import core.VectorIndex;
import dataset.DatasetLoader;
import index.hnsw.JVectorHNSWIndex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SyncAsyncBenchmark {
    private static final String BASE_PATH = "/Users/kartikeysrivastava/Desktop/projects/dataset/custom-100k/";
    private static final String BASE_VECTORS = BASE_PATH + "base.fvecs";
    private static final String QUERY_VECTORS = BASE_PATH + "query.fvecs";
    private static final String GROUND_TRUTH = BASE_PATH + "groundtruth.ivecs";

    private static final int K = 10;
    private static final int VECTORS_TO_REINSERT = 10000;
    private static final int WARMUP_DELETE_COUNT = 1000;

    // =======================
    // Configuration
    // =======================
    private record BenchmarkConfig(int m, int efConstruction, int efSearch, int threads) {
        @Override
        public String toString() {
            return String.format("M=%d, efC=%d, efS=%d, threads=%d", m, efConstruction, efSearch, threads);
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println("=== Automated Batch Insert Benchmark: HNSW ===\n");

        // Load dataset once
        List<Vector> indexVectors = DatasetLoader.loadFVectors(BASE_VECTORS);
        List<Vector> queryVectors = DatasetLoader.loadFVectors(QUERY_VECTORS);
        List<int[]> groundTruth = DatasetLoader.loadIVecs(GROUND_TRUTH);
        System.out.println("Dataset: " + indexVectors.size() + " vectors, " + queryVectors.size() + " queries\n");

        // Define all test configurations
        List<BenchmarkConfig> configs = getTestConfigurations();

        System.out.println("Total configurations to test: " + configs.size() + "\n");
        System.out.println("========================================\n");

        // Warmup once with baseline config
        runWarmup(indexVectors, queryVectors, groundTruth, new BenchmarkConfig(16, 200, 200, 12));

        // Prepare test data
        List<String> idsToDelete = new ArrayList<>();
        List<Vector> vectorsToReinsert = new ArrayList<>();
        for (int i = 0; i < VECTORS_TO_REINSERT; i++) {
            idsToDelete.add(indexVectors.get(i).id());
            vectorsToReinsert.add(indexVectors.get(i));
        }

        // Run all benchmarks
        List<BenchmarkSummary> results = new ArrayList<>();
        int configNum = 1;

        for (BenchmarkConfig config : configs) {
            System.out.println("========================================");
            System.out.println("Configuration " + configNum + "/" + configs.size() + ": " + config);
            System.out.println("========================================\n");

            ExecutorService executor = BenchmarkExecutors.createCustomExecutor(config.threads);

            try {
                // Run parallel test
                InsertResult parallelInsert = runInsertTest(
                        "Parallel Batch Inserts",
                        indexVectors, idsToDelete, vectorsToReinsert,
                        config, true, executor
                );
                SearchResult parallelSearch = runSearchTests(parallelInsert.index, queryVectors, groundTruth, executor);

                // Run sequential test
                InsertResult sequentialInsert = runInsertTest(
                        "Sequential Inserts (Baseline)",
                        indexVectors, idsToDelete, vectorsToReinsert,
                        config, false, null
                );
                SearchResult sequentialSearch = runSearchTests(sequentialInsert.index, queryVectors, groundTruth, executor);

                // Store results
                results.add(new BenchmarkSummary(
                        config,
                        sequentialInsert, parallelInsert,
                        sequentialSearch, parallelSearch
                ));

                // Print comparison
                printInsertComparison(sequentialInsert, parallelInsert);
                printSearchComparison(sequentialSearch, parallelSearch);

            } finally {
                executor.shutdown();
            }

            configNum++;
            System.out.println("\n");
        }

        // Print final summary
        printFinalSummary(results);
    }

    // =======================
    // Test Configurations
    // =======================
    private static List<BenchmarkConfig> getTestConfigurations() {
        List<BenchmarkConfig> configs = new ArrayList<>();

        configs.add(new BenchmarkConfig(16, 200, 200, 8));

        // 1. Thread count sweep (M=16, efC=200, efS=200)
        System.out.println("Test Suite 1: Thread Count Sweep");
        for (int threads : new int[]{1, 2, 4, 6, 8, 10, 12, 16, 20, 24}) {
            if (threads!=12) continue;
            configs.add(new BenchmarkConfig(16, 200, 200, threads));
        }

        // 2. M parameter sweep (efC=200, efS=200, threads=12)
        System.out.println("Test Suite 2: M Parameter Sweep");
        for (int m : new int[]{8, 12, 16, 24, 32}) {
            if (m != 16) continue;
            configs.add(new BenchmarkConfig(m, 200, 200, 12));
        }

        // 3. efConstruction sweep (M=16, efS=200, threads=12)
        System.out.println("Test Suite 3: efConstruction Sweep");
        for (int efC : new int[]{100, 200, 400, 600}) {
            if (efC!=200) continue;
            configs.add(new BenchmarkConfig(16, efC, 200, 12));
        }

        // 4. efSearch sweep (M=16, efC=200, threads=12)
        System.out.println("Test Suite 4: efSearch Sweep");
        for (int efS : new int[]{50, 100, 200, 400, 800}) {
            if (efS!=100) continue;
            configs.add(new BenchmarkConfig(16, 200, efS, 12));
        }

        // 5. Special configurations
        System.out.println("Test Suite 5: Special Configurations");
        configs.add(new BenchmarkConfig(12, 200, 200, 12));  // Fast & Good
        configs.add(new BenchmarkConfig(24, 400, 400, 12));  // Quality-focused
        configs.add(new BenchmarkConfig(8, 100, 100, 12));   // Speed-focused
        configs.add(new BenchmarkConfig(16, 200, 200, 24));  // High-concurrency

        return configs;
    }

    // =======================
    // Warmup
    // =======================
    private static void runWarmup(List<Vector> indexVectors, List<Vector> queryVectors,
                                  List<int[]> groundTruth, BenchmarkConfig config) {
        System.out.println("=== Warmup Phase (" + config + ") ===");
        VectorIndex warmupIndex = new JVectorHNSWIndex(config.m, config.efConstruction, config.efSearch);
        warmupIndex.build(indexVectors);

        double recall = calculateAverageRecall(warmupIndex, queryVectors, groundTruth);
        System.out.println("Recall@10 on fresh index: " + recall);

        for (int i = 0; i < WARMUP_DELETE_COUNT; i++) {
            warmupIndex.delete(indexVectors.get(i).id());
        }
        for (int i = 0; i < WARMUP_DELETE_COUNT; i++) {
            warmupIndex.insert(indexVectors.get(i));
        }

        System.out.println("Warmup complete\n");
    }

    // =======================
    // Insert Benchmarking
    // =======================
    private static InsertResult runInsertTest(
            String testName,
            List<Vector> indexVectors,
            List<String> idsToDelete,
            List<Vector> vectorsToReinsert,
            BenchmarkConfig config,
            boolean parallel,
            ExecutorService executor
    ) {
        System.out.println("=== " + testName + " ===");

        // Build index
        VectorIndex index = parallel
                ? new JVectorHNSWIndex(config.m, config.efConstruction, config.efSearch, executor)
                : new JVectorHNSWIndex(config.m, config.efConstruction, config.efSearch);

        long buildStart = System.currentTimeMillis();
        index.build(indexVectors);
        long buildTime = System.currentTimeMillis() - buildStart;

        System.out.println("Build time: " + buildTime + " ms");
        System.out.println("Initial size: " + index.size() + " vectors");

        // Delete vectors
        for (String id : idsToDelete) {
            index.delete(id);
        }

        // Re-insert vectors
        long startTime = System.currentTimeMillis();
        if (parallel) {
            index.insertAsync(vectorsToReinsert);
        } else {
            for (Vector v : vectorsToReinsert) {
                index.insert(v);
            }
        }
        long insertTime = System.currentTimeMillis() - startTime;
        double throughput = 1000.0 * VECTORS_TO_REINSERT / insertTime;

        System.out.println("Insert time: " + insertTime + " ms");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " inserts/sec\n");

        return new InsertResult(testName, index, buildTime, insertTime, throughput);
    }

    // =======================
    // Search Benchmarking
    // =======================
    private static SearchResult runSearchTests(
            VectorIndex index,
            List<Vector> queryVectors,
            List<int[]> groundTruth,
            ExecutorService executor
    ) {
        System.out.println("--- Search Performance ---");

        // Sequential search
        long seqStart = System.currentTimeMillis();
        Metrics seqMetrics = BenchmarkRunner.measureSearchOnly(index, queryVectors, K, "sift");
        long seqTime = System.currentTimeMillis() - seqStart;
        double seqRecall = calculateAverageRecall(index, queryVectors, groundTruth);
        double seqThroughput = 1000.0 * queryVectors.size() / seqTime;

        System.out.println("Sequential: " + String.format("%.2f qps, %.2f μs, recall=%.4f",
                seqThroughput, seqMetrics.getQueryLatencyP50Micros(), seqRecall));

        // Concurrent search
        long concStart = System.currentTimeMillis();
        List<CompletableFuture<List<QueryResult>>> futures = queryVectors.stream()
                .map(v -> index.searchAsync(v.vector(), K, "sift"))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long concTime = System.currentTimeMillis() - concStart;
        double concThroughput = 1000.0 * queryVectors.size() / concTime;

        Metrics concMetrics = BenchmarkRunner.measureSearchOnly(index, queryVectors, K, "sift");
        double concRecall = calculateAverageRecall(index, queryVectors, groundTruth);

        System.out.println("Concurrent: " + String.format("%.2f qps, %.2f μs, recall=%.4f",
                concThroughput, concMetrics.getQueryLatencyP50Micros(), concRecall));
        System.out.println();

        return new SearchResult(
                seqTime, seqThroughput, seqMetrics.getQueryLatencyP50Micros(), seqRecall,
                concTime, concThroughput, concMetrics.getQueryLatencyP50Micros(), concRecall
        );
    }

    // =======================
    // Comparison & Printing
    // =======================
    private static void printInsertComparison(InsertResult seq, InsertResult parallel) {
        double speedup = (double) seq.insertTime / parallel.insertTime;
        System.out.println("Insert Speedup: " + String.format("%.2fx", speedup) +
                " (seq: " + seq.insertTime + "ms, parallel: " + parallel.insertTime + "ms)\n");
    }

    private static void printSearchComparison(SearchResult seq, SearchResult parallel) {
        double concSpeedup = parallel.concThroughput / seq.concThroughput;
        System.out.println("Concurrent Search: " + String.format("%.2fx speedup", concSpeedup) +
                " (seq-index: " + String.format("%.2f", seq.concThroughput) + " qps, " +
                "parallel-index: " + String.format("%.2f", parallel.concThroughput) + " qps)\n");
    }

    // =======================
    // Final Summary
    // =======================
    private static void printFinalSummary(List<BenchmarkSummary> results) {
        System.out.println("\n========================================");
        System.out.println("FINAL SUMMARY - ALL CONFIGURATIONS");
        System.out.println("========================================\n");

        System.out.printf("%-40s | %10s | %10s | %10s | %10s | %10s%n",
                "Config", "Build(ms)", "InsSpeedup", "Ins(ms)", "SearchQPS", "ConcSpeedup");
        System.out.println("-".repeat(120));

        for (BenchmarkSummary summary : results) {
            double insertSpeedup = (double) summary.seqInsert.insertTime / summary.parallelInsert.insertTime;
            double concSpeedup = summary.parallelSearch.concThroughput / summary.seqSearch.concThroughput;

            System.out.printf("%-40s | %10d | %9.2fx | %10d | %10.0f | %9.2fx%n",
                    summary.config.toString(),
                    summary.parallelInsert.buildTime,
                    insertSpeedup,
                    summary.parallelInsert.insertTime,
                    summary.parallelSearch.concThroughput,
                    concSpeedup
            );
        }

        // Find best configurations
        System.out.println("\n" + "=".repeat(120));
        System.out.println("BEST CONFIGURATIONS:");
        System.out.println("=".repeat(120));

        BenchmarkSummary fastestInsert = results.stream()
                .min((a, b) -> Long.compare(a.parallelInsert.insertTime, b.parallelInsert.insertTime))
                .orElse(null);
        System.out.println("Fastest Insert: " + fastestInsert.config + " (" + fastestInsert.parallelInsert.insertTime + " ms)");

        BenchmarkSummary bestConcSearch = results.stream()
                .max((a, b) -> Double.compare(a.parallelSearch.concThroughput, b.parallelSearch.concThroughput))
                .orElse(null);
        System.out.println("Best Concurrent Search: " + bestConcSearch.config +
                " (" + String.format("%.2f", bestConcSearch.parallelSearch.concThroughput) + " qps)");

        BenchmarkSummary bestRecall = results.stream()
                .max((a, b) -> Double.compare(a.parallelSearch.seqRecall, b.parallelSearch.seqRecall))
                .orElse(null);
        System.out.println("Best Recall: " + bestRecall.config +
                " (recall=" + String.format("%.4f", bestRecall.parallelSearch.seqRecall) + ")");
    }

    // =======================
    // Utilities
    // =======================
    private static double calculateAverageRecall(
            VectorIndex index,
            List<Vector> queryVectors,
            List<int[]> groundTruth
    ) {
        List<Double> recalls = new ArrayList<>();
        for (int i = 0; i < queryVectors.size(); i++) {
            List<QueryResult> results = index.search(queryVectors.get(i).vector(), K, "sift");
            recalls.add(BenchmarkRunner.calculateRecall(results, groundTruth.get(i), K));
        }
        return recalls.stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    // =======================
    // Result DTOs
    // =======================
    private record InsertResult(String name, VectorIndex index, long buildTime, long insertTime, double throughput) {}

    private record SearchResult(
            long seqTime, double seqThroughput, double seqP50, double seqRecall,
            long concTime, double concThroughput, double concP50, double concRecall
    ) {}

    private record BenchmarkSummary(
            BenchmarkConfig config,
            InsertResult seqInsert,
            InsertResult parallelInsert,
            SearchResult seqSearch,
            SearchResult parallelSearch
    ) {}
}
package benchmark.POC1;

import benchmark.BenchmarkRunner;
import core.QueryResult;
import core.Vector;
import dataset.DatasetLoader;
import index.hnsw.JVectorHNSWIndex;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConcurrentSearchTest {

    private static final int K = 10;
    private static final int NUM_SEARCH_THREADS = 4;

    private static final String BASE_PATH =
            "/Users/kartikeysrivastava/Desktop/projects/dataset/cohere-768/";
    private static final String BASE_VECTORS = BASE_PATH + "cohere_base.fvecs";
    private static final String QUERY_VECTORS = BASE_PATH + "cohere_query.fvecs";
    private static final String GROUND_TRUTH = BASE_PATH + "cohere_groundtruth.ivecs";

    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("Loading COHERENT dataset...");
        List<Vector> indexVectors = DatasetLoader.loadFVectors(BASE_VECTORS);
        List<Vector> queryVectors = DatasetLoader.loadFVectors(QUERY_VECTORS);
        List<int[]> groundTruth = DatasetLoader.loadIVecs(GROUND_TRUTH);
        System.out.println("Dataset: " + indexVectors.size() + " vectors, " + queryVectors.size() + " queries");

        System.out.println("\nBuilding index...");
        JVectorHNSWIndex index = new JVectorHNSWIndex(16, 100, 200);
        index.build(indexVectors);
        System.out.println("Index built. Size: " + index.size());

        // =======================
        // Test at 50% deletion
        // =======================
        System.out.println("\n=== Deleting 50% of vectors ===");
        Set<String> deletedIds = new HashSet<>();
        int fiftyPercent = indexVectors.size() / 2;
        for (int i = 0; i < fiftyPercent; i++) {
            String id = indexVectors.get(i).id();
            index.delete(id);
            deletedIds.add(id);
        }
        System.out.println("Deleted " + fiftyPercent + " vectors. Live: " + index.size());

        runConcurrentSearchTest(index, queryVectors, groundTruth, deletedIds, "50% deletion");

        // =======================
        // Test at 90% deletion
        // =======================
        System.out.println("\n=== Deleting another 40% of vectors (cumulative 90%) ===");
        int ninetyPercent = (int) (indexVectors.size() * 0.9);
        for (int i = fiftyPercent; i < ninetyPercent; i++) {
            String id = indexVectors.get(i).id();
            index.delete(id);
            deletedIds.add(id);
        }
        System.out.println("Deleted another " + (ninetyPercent - fiftyPercent) + " vectors. Live: " + index.size());

        runConcurrentSearchTest(index, queryVectors, groundTruth, deletedIds, "90% deletion");
    }

    private static void runConcurrentSearchTest(
            JVectorHNSWIndex index,
            List<Vector> queryVectors,
            List<int[]> groundTruth,
            Set<String> deletedIds,
            String label
    ) throws InterruptedException {
        System.out.println("\n=== Concurrent Search Test at " + label + " ===");
        System.out.println("Search threads: " + NUM_SEARCH_THREADS);

        ExecutorService searchPool = Executors.newFixedThreadPool(NUM_SEARCH_THREADS);

        // ========================
        // Phase 1: Before cleanup
        // ========================
        System.out.println("\n-- Phase 1: Before cleanup --");
        SearchStats beforeStats = runConcurrentBatch(index, queryVectors, groundTruth, deletedIds, K, searchPool);
        printStats(beforeStats, "Before cleanup");

        // ========================
        // Phase 2: During cleanup
        // ========================
        System.out.println("\n-- Phase 2: During cleanup --");

        List<Double> allDuringLatencies = new ArrayList<>();
        List<Double> batchRecalls = new ArrayList<>();

        AtomicBoolean cleanupDone = new AtomicBoolean(false);
        long cleanupStart = System.nanoTime();

        CompletableFuture<Void> cleanupFuture = CompletableFuture.runAsync(() -> {
            index.cleanup();
            cleanupDone.set(true);
        });

        int batchCount = 0;
        while (!cleanupDone.get()) {
            SearchStats batchStats = runConcurrentBatch(index, queryVectors, groundTruth, deletedIds, K, searchPool);
            allDuringLatencies.addAll(batchStats.latenciesMicros);
            batchRecalls.add(batchStats.recall);
            batchCount++;
        }

        try {
            cleanupFuture.get();
        } catch (ExecutionException e) {
            System.err.println("Cleanup failed: " + e.getMessage());
        }

        long cleanupTimeMs = (System.nanoTime() - cleanupStart) / 1_000_000;
        System.out.println("Cleanup completed in: " + cleanupTimeMs + " ms");
        System.out.println("Batches fired during cleanup: " + batchCount);

        if (!allDuringLatencies.isEmpty()) {
            Collections.sort(allDuringLatencies);
            double p50 = allDuringLatencies.get(allDuringLatencies.size() / 2);
            double p95 = allDuringLatencies.get((int) (allDuringLatencies.size() * 0.95));
            double p99 = allDuringLatencies.get((int) (allDuringLatencies.size() * 0.99));
            double avgRecall = batchRecalls.stream().mapToDouble(d -> d).average().orElse(0.0);
            double minRecall = Collections.min(batchRecalls);
            double maxRecall = Collections.max(batchRecalls);
            System.out.printf("During cleanup - Latency P50: %.2f μs, P95: %.2f μs, P99: %.2f μs%n", p50, p95, p99);
            System.out.printf("During cleanup - Recall avg: %.4f, min: %.4f, max: %.4f%n", avgRecall, minRecall, maxRecall);
        } else {
            System.out.println("During cleanup - No batches completed (cleanup was too fast)");
        }

        // ========================
        // Phase 3: After cleanup
        // ========================
        System.out.println("\n-- Phase 3: After cleanup --");
        SearchStats afterStats = runConcurrentBatch(index, queryVectors, groundTruth, deletedIds, K, searchPool);
        printStats(afterStats, "After cleanup");

        searchPool.shutdown();
        searchPool.awaitTermination(30, TimeUnit.SECONDS);
    }

    private static SearchStats runConcurrentBatch(
            JVectorHNSWIndex index,
            List<Vector> queryVectors,
            List<int[]> groundTruth,
            Set<String> deletedIds,
            int k,
            ExecutorService searchPool
    ) throws InterruptedException {
        int total = queryVectors.size();
        int numThreads = ((ThreadPoolExecutor) searchPool).getCorePoolSize();
        int chunkSize = (int) Math.ceil((double) total / numThreads);

        List<Future<ThreadResult>> futures = new ArrayList<>();

        for (int t = 0; t < numThreads; t++) {
            int start = t * chunkSize;
            int end = Math.min(start + chunkSize, total);
            if (start >= total) break;

            final int s = start;
            final int e = end;

            futures.add(searchPool.submit(() -> {
                List<Double> latencies = new ArrayList<>();
                int matches = 0;
                int totalPossible = 0;

                for (int i = s; i < e; i++) {
                    long startNs = System.nanoTime();
                    List<QueryResult> results = index.search(queryVectors.get(i).vector(), k, "coherent");
                    long endNs = System.nanoTime();
                    latencies.add((endNs - startNs) / 1000.0);

                    // filter ground truth to only living vectors
                    int[] rawGT = groundTruth.get(i);
                    List<Integer> filteredGT = new ArrayList<>();
                    for (int gtId : rawGT) {
                        if (!deletedIds.contains("cohere_" + gtId)) {
                            filteredGT.add(gtId);
                            if (filteredGT.size() == k) break;
                        }
                    }

                    matches += BenchmarkRunner.calculateRecallCount(results, filteredGT, k);
                    totalPossible += filteredGT.size();
                }

                return new ThreadResult(latencies, matches, totalPossible);
            }));
        }

        List<Double> allLatencies = new ArrayList<>();
        int totalMatches = 0;
        int totalPossible = 0;

        for (Future<ThreadResult> future : futures) {
            try {
                ThreadResult result = future.get();
                allLatencies.addAll(result.latencies);
                totalMatches += result.matches;
                totalPossible += result.totalPossible;
            } catch (ExecutionException ex) {
                System.err.println("Search thread failed: " + ex.getMessage());
            }
        }

        Collections.sort(allLatencies);
        double recall = totalPossible > 0 ? (double) totalMatches / totalPossible : 0.0;
        return new SearchStats(allLatencies, recall);
    }

    private static void printStats(SearchStats stats, String phase) {
        if (stats.latenciesMicros.isEmpty()) return;
        double p50 = stats.latenciesMicros.get(stats.latenciesMicros.size() / 2);
        double p95 = stats.latenciesMicros.get((int) (stats.latenciesMicros.size() * 0.95));
        double p99 = stats.latenciesMicros.get((int) (stats.latenciesMicros.size() * 0.99));
        System.out.printf("%s - Latency P50: %.2f μs, P95: %.2f μs, P99: %.2f μs%n", phase, p50, p95, p99);
        System.out.printf("%s - Recall: %.4f%n", phase, stats.recall);
    }

    private record ThreadResult(List<Double> latencies, int matches, int totalPossible) {}

    private record SearchStats(List<Double> latenciesMicros, double recall) {}
}
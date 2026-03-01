package benchmark.POC1;

import benchmark.BenchmarkRunner;
import benchmark.DeleteMetrics;
import benchmark.Metrics;
import core.QueryResult;
import core.Vector;
import core.VectorIndex;
import dataset.DatasetLoader;
import index.hnsw.JVectorHNSWIndex;
import io.github.jbellis.jvector.graph.OnHeapGraphIndex;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CompactionPOC1 {

    private static final int K = 10;

    // =======================
    // Dataset Paths
    // =======================
    private static final String BASE_PATH =
            "/Users/kartikeysrivastava/Desktop/projects/dataset/cohere-768/";
    private static final String BASE_VECTORS = BASE_PATH + "cohere_base.fvecs";
    private static final String QUERY_VECTORS = BASE_PATH + "cohere_query.fvecs";
    private static final String GROUND_TRUTH = BASE_PATH + "cohere_groundtruth.ivecs";

    public static void main(String[] args) throws IOException, InterruptedException {

        System.out.println("Loading COHERE dataset...");
        List<Vector> indexVectors = DatasetLoader.loadFVectors(BASE_VECTORS);
        List<Vector> queryVectors = DatasetLoader.loadFVectors(QUERY_VECTORS);
        List<int[]> groundTruth = DatasetLoader.loadIVecs(GROUND_TRUTH);

        System.out.println("Dataset: " + indexVectors.size() + " vectors, "
                + queryVectors.size() + " queries");

        // =======================
        // Create Index
        // =======================
        System.out.println("Creating HNSW index...");
        JVectorHNSWIndex index = new JVectorHNSWIndex(16, 100, 200);

        // =======================
        // Test 1: Initial Build + Query
        // =======================
        runInitialBuildAndQuery(index, indexVectors, queryVectors, K);

        double initialRecall =
                calculateAverageRecall(index, queryVectors, groundTruth, K, new HashSet<>());

        System.out.printf("Recall@%d: %.4f%n", K, initialRecall);

        //Add deletion waves at 1%, 5%, 10%, 25%, and 50% of total vectors
        System.out.println("=== Test 2: Delete performance (1%, 5%, 10%, 25%, and 50% of total vectors) ===");

        List<String> idsToDelete = new ArrayList<>();
        List<Integer> vectorsToDeleteInPercentage = new ArrayList<>();
        addVectorsInList(vectorsToDeleteInPercentage);

        Set<String> deletedIds = new HashSet<>();
        int offset = 0;
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        for (int percentToDelete : vectorsToDeleteInPercentage) {
            int vectorsToDelete = (int) (indexVectors.size() * percentToDelete / 100.0);
            offset = collectDeleteAndReinsertVectors(
                    offset, vectorsToDelete, indexVectors, idsToDelete
            );
            DeleteMetrics deleteMetrics =
                    BenchmarkRunner.benchmarkDeletes(index, idsToDelete);

            deletedIds.addAll(idsToDelete);

            System.out.println("\n" + deleteMetrics);
            System.out.println("Index size after deletes: " + index.size() + " vectors");
            double recallBeforeCleanup = calculateAverageRecall(index, queryVectors, groundTruth, K, deletedIds);
            System.out.printf("Recall@%d before cleanup: %.4f%n", K, recallBeforeCleanup);

            if (deletedIds.size() == indexVectors.size() * 9/10) {
                OnHeapGraphIndex g = index.getGraph();
                int ub = g.getIdUpperBound();
                int live = g.size(0);
                System.out.printf("Ordinal space BEFORE cleanup: upperBound=%d, live=%d, holes=%d (%.1f%% fragmented)%n", ub, live, ub - live, (ub-live)*100.0/ub);
            }

            long beforeCleanup = memoryBean.getHeapMemoryUsage().getUsed();
            long cleanupStart = System.nanoTime();
            index.cleanup();
            long cleanupTimeMs = (System.nanoTime() - cleanupStart) / 1_000_000;

            System.gc();
            Thread.sleep(100); // give GC a moment to run
            long afterCleanup = memoryBean.getHeapMemoryUsage().getUsed();

            if (deletedIds.size() == indexVectors.size() * 9/10) {
                OnHeapGraphIndex g = index.getGraph();
                int ub = g.getIdUpperBound();
                int live = g.size(0);
                System.out.printf("Ordinal space AFTER cleanup: upperBound=%d, live=%d, holes=%d (%.1f%% fragmented)%n", ub, live, ub - live, (ub-live)*100.0/ub);
            }

            int cumulativePercent = deletedIds.size() * 100 / indexVectors.size();
            System.out.printf("Cleanup time (cumulative %d%% deleted, %d vectors): %d ms%n",
                    cumulativePercent, deletedIds.size(), cleanupTimeMs);

            long peakMemoryMB = beforeCleanup / (1024 * 1024);
            long afterMemoryMB = afterCleanup / (1024 * 1024);

            System.out.printf("Heap before cleanup: %d MB%n", peakMemoryMB);
            System.out.printf("Heap after cleanup: %d MB%n", afterMemoryMB);

            double recallAfterCleanup = calculateAverageRecall(index, queryVectors, groundTruth, K, deletedIds);
            System.out.printf("Recall@%d after cleanup: %.4f%n", K, recallAfterCleanup);
        }
    }

    private static void addVectorsInList(List<Integer> vectorsToDeleteInPercentage) {
        for (int i = 0; i < 9; i++) {
            vectorsToDeleteInPercentage.add(10);
        }
    }

    private static int collectDeleteAndReinsertVectors(
            int startIndex,
            int count,
            List<Vector> indexVectors,
            List<String> idsToDelete
    ) {
        idsToDelete.clear();

        for (int i = startIndex; i < startIndex + count; i++) {
            Vector vector = indexVectors.get(i);
            idsToDelete.add(vector.id());
        }
        return startIndex + count; // new offset for next wave
    }

    private static Metrics runInitialBuildAndQuery(
            VectorIndex index,
            List<Vector> indexVectors,
            List<Vector> queryVectors,
            int k
    ) throws InterruptedException {

        System.out.println("\n=== Test 1: Initial Build + Query ===");

        Metrics metrics =
                BenchmarkRunner.run(index, indexVectors, queryVectors, k, "cohere");

        System.out.println("Build Time: " + metrics.getBuildTimeMs() + " ms");
        System.out.println("Build Memory: " + metrics.getBuildMemoryMB() + " MB");
        System.out.println("Query Latency P50: " + metrics.getQueryLatencyP50Micros() + " μs");
        System.out.println("Throughput: " + metrics.getThroughputQPS() + " QPS");
        System.out.println("Index Size: " + index.size() + " vectors");

        return metrics;
    }

    private static double calculateAverageRecall(
            VectorIndex index,
            List<Vector> queryVectors,
            List<int[]> groundTruth,
            int k,
            Set<String> deletedIds  // track what's been deleted
    ) {
        List<Double> recalls = new ArrayList<>();

        for (int i = 0; i < queryVectors.size(); i++) {
            List<QueryResult> results = index.search(queryVectors.get(i).vector(), k, "cohere");

            // filter ground truth to only living vectors
            int[] rawGT = groundTruth.get(i);
            List<Integer> filteredGT = new ArrayList<>();
            for (int gtId : rawGT) {
                if (!deletedIds.contains("cohere_" + gtId)) {
                    filteredGT.add(gtId);
                    if (filteredGT.size() == k) break;
                }
            }

            double recall = BenchmarkRunner.calculateRecall(results, filteredGT.stream().mapToInt(Integer::intValue).toArray(), k);
            recalls.add(recall);
        }

        return recalls.stream().mapToDouble(d -> d).average().orElse(0.0);
    }
}

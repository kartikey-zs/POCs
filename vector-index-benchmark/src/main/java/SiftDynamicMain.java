import benchmark.*;
import core.QueryResult;
import core.Vector;
import core.VectorIndex;
import dataset.DatasetLoader;
import index.hnsw.JVectorHNSWIndex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SiftDynamicMain {

    // =======================
    // Configuration
    // =======================
    private static final int VECTORS_TO_DELETE = 10000;
    private static final int FINAL_DELETES = 50000;
    private static final int K = 10;

    // =======================
    // Dataset Paths
    // =======================
    private static final String BASE_PATH =
            "/Users/kartikeysrivastava/Desktop/projects/dataset/sift-1M/";

    private static final String BASE_VECTORS = BASE_PATH + "sift_base.fvecs";
    private static final String QUERY_VECTORS = BASE_PATH + "sift_query.fvecs";
    private static final String GROUND_TRUTH = BASE_PATH + "sift_groundtruth.ivecs";

    public static void main(String[] args) throws IOException, InterruptedException {

        System.out.println("=== Dynamic Benchmark: HNSW Index (SIFT Dataset) ===");

        // =======================
        // Load Dataset
        // =======================
        System.out.println("Loading SIFT dataset...");
        List<Vector> indexVectors = DatasetLoader.loadFVectors(BASE_VECTORS);
        List<Vector> queryVectors = DatasetLoader.loadFVectors(QUERY_VECTORS);
        List<int[]> groundTruth = DatasetLoader.loadIVecs(GROUND_TRUTH);

        System.out.println("Dataset: " + indexVectors.size() + " vectors, "
                + queryVectors.size() + " queries");

        // =======================
        // Create Index
        // =======================
        System.out.println("Creating HNSW index...");
        VectorIndex index = new JVectorHNSWIndex(16, 100, 200);

        // =======================
        // Test 1: Initial Build + Query
        // =======================
        Metrics initialMetrics =
                runInitialBuildAndQuery(index, indexVectors, queryVectors, K);

        double initialRecall =
                calculateAverageRecall(index, queryVectors, groundTruth, K);

        System.out.printf("Recall@%d: %.4f%n", K, initialRecall);

        // =======================
        // Test 2: Delete 1k vectors
        // =======================
        System.out.println("=== Test 2: Delete performance (1000 vectors) ===");

        List<String> idsToDelete = new ArrayList<>();
        List<Vector> vectorsToReinsert = new ArrayList<>();
        collectDeleteAndReinsertVectors(
                VECTORS_TO_DELETE, indexVectors, idsToDelete, vectorsToReinsert
        );

        DeleteMetrics deleteMetrics =
                BenchmarkRunner.benchmarkDeletes(index, idsToDelete);

        System.out.println("\n" + deleteMetrics);
        System.out.println("Index size after deletes: " + index.size() + " vectors");

        Metrics afterDeleteMetrics =
                measureSearchAfterDelete(index, queryVectors, K);

        double afterDeleteRecall =
                calculateAverageRecall(index, queryVectors, groundTruth, K);

        System.out.printf("Recall@%d: %.4f%n", K, afterDeleteRecall);

        double recallDrop =
                ((afterDeleteRecall - initialRecall) / initialRecall) * 100;

        System.out.printf("Recall change from deletion: %.2f%%%n", recallDrop);

        // =======================
        // Test 3: Re-insert 1k vectors
        // =======================
        InsertMetrics insertMetrics =
                benchmarkReinsert(index, vectorsToReinsert);

        // =======================
        // Test 4: Search after re-insert
        // =======================
        Metrics afterInsertMetrics =
                measureSearchAfterInsert(index, queryVectors, K, afterDeleteMetrics);

        double afterInsertRecall =
                calculateAverageRecall(index, queryVectors, groundTruth, K);

        System.out.printf("Recall@%d: %.4f%n", K, afterInsertRecall);

        double recallRecovery =
                ((afterInsertRecall - afterDeleteRecall) / afterDeleteRecall) * 100;

        System.out.printf("Recall recovery from re-insertion: %.2f%%%n", recallRecovery);

        double insertDegradation =
                ((afterInsertMetrics.getQueryLatencyP50Micros()
                        - initialMetrics.getQueryLatencyP50Micros())
                        / initialMetrics.getQueryLatencyP50Micros()) * 100;

        System.out.printf(
                "Latency change from baseline: %.1f%% %s%n",
                Math.abs(insertDegradation),
                insertDegradation > 0 ? "slower" : "faster"
        );

        // =======================
        // Test 5: Large Deletion (5k)
        // =======================
        System.out.println("=== Test 5: Large deletion performance and search degradation ===");

        List<String> moreIdsToDelete = new ArrayList<>();
        for (int i = VECTORS_TO_DELETE; i < VECTORS_TO_DELETE + FINAL_DELETES; i++) {
            moreIdsToDelete.add(indexVectors.get(i).id());
        }

        SearchDegradationMetrics degradationMetrics =
                BenchmarkRunner.benchmarkSearchDegradation(
                        index, queryVectors, K, "sift", moreIdsToDelete
                );

        System.out.println("\n" + degradationMetrics);
        System.out.println("Final index size: " + index.size() + " vectors");

        double finalRecall =
                calculateAverageRecall(index, queryVectors, groundTruth, K);

        System.out.printf("Recall@%d: %.4f%n", K, finalRecall);

        // =======================
        // Summary
        // =======================
        printSummary(
                indexVectors.size(),
                initialRecall,
                afterDeleteRecall,
                afterInsertRecall,
                finalRecall,
                recallDrop,
                recallRecovery,
                insertDegradation,
                deleteMetrics,
                insertMetrics,
                degradationMetrics,
                index.size()
        );
    }

    // =========================================================
    // Helper Methods
    // =========================================================

    private static Metrics runInitialBuildAndQuery(
            VectorIndex index,
            List<Vector> indexVectors,
            List<Vector> queryVectors,
            int k
    ) throws InterruptedException {

        System.out.println("\n=== Test 1: Initial Build + Query ===");

        Metrics metrics =
                BenchmarkRunner.run(index, indexVectors, queryVectors, k, "sift");

        System.out.println("Build Time: " + metrics.getBuildTimeMs() + " ms");
        System.out.println("Build Memory: " + metrics.getBuildMemoryMB() + " MB");
        System.out.println("Query Latency P50: " + metrics.getQueryLatencyP50Micros() + " μs");
        System.out.println("Throughput: " + metrics.getThroughputQPS() + " QPS");
        System.out.println("Index Size: " + index.size() + " vectors");

        return metrics;
    }

    private static Metrics measureSearchAfterDelete(
            VectorIndex index,
            List<Vector> queryVectors,
            int k
    ) {
        Metrics metrics =
                BenchmarkRunner.measureSearchOnly(index, queryVectors, k, "sift");

        System.out.println("Query latency p50: "
                + metrics.getQueryLatencyP50Micros() + " microSeconds");
        System.out.println("QPS: " + metrics.getThroughputQPS());

        return metrics;
    }

    private static InsertMetrics benchmarkReinsert(
            VectorIndex index,
            List<Vector> vectorsToReinsert
    ) {
        System.out.println("=== Test 3: Insert Performance (re-inserting 1000 vectors) ===");

        InsertMetrics metrics =
                BenchmarkRunner.benchmarkInserts(index, vectorsToReinsert);

        System.out.println("\n" + metrics);
        System.out.println("Index size after inserts: " + index.size() + " vectors");

        return metrics;
    }

    private static Metrics measureSearchAfterInsert(
            VectorIndex index,
            List<Vector> queryVectors,
            int k,
            Metrics afterDeleteMetrics
    ) {
        System.out.println("=== Test 4: Search performance after re-insertion ===");

        Metrics metrics =
                BenchmarkRunner.measureSearchOnly(index, queryVectors, k, "sift");

        System.out.println("Query latency p50: " + metrics.getQueryLatencyP50Micros());
        System.out.println("QPS: " + afterDeleteMetrics.getThroughputQPS());

        return metrics;
    }

    private static void collectDeleteAndReinsertVectors(
            int count,
            List<Vector> indexVectors,
            List<String> idsToDelete,
            List<Vector> vectorsToReinsert
    ) {
        for (int i = 0; i < count; i++) {
            Vector vector = indexVectors.get(i);
            idsToDelete.add(vector.id());
            vectorsToReinsert.add(vector);
        }
    }

    private static double calculateAverageRecall(
            VectorIndex index,
            List<Vector> queryVectors,
            List<int[]> groundTruth,
            int k
    ) {
        List<Double> recalls = new ArrayList<>();

        for (int i = 0; i < queryVectors.size(); i++) {
            List<QueryResult> results =
                    index.search(queryVectors.get(i).vector(), k, "sift");

            double recall =
                    BenchmarkRunner.calculateRecall(results, groundTruth.get(i), k);

            recalls.add(recall);
        }

        return recalls.stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    private static void printSummary(
            int initialSize,
            double initialRecall,
            double afterDeleteRecall,
            double afterInsertRecall,
            double finalRecall,
            double recallDrop,
            double recallRecovery,
            double insertDegradation,
            DeleteMetrics deleteMetrics,
            InsertMetrics insertMetrics,
            SearchDegradationMetrics degradationMetrics,
            int finalSize
    ) {
        System.out.println("\n=== Summary ===");
        System.out.println("Started with: " + initialSize + " vectors");
        System.out.println("Deleted: " + VECTORS_TO_DELETE + " vectors (then re-inserted)");
        System.out.println("Finally deleted: " + FINAL_DELETES + " more vectors");
        System.out.println("Final size: " + finalSize + " vectors");

        System.out.println("\nPerformance Impact:");
        System.out.printf("  Delete latency (1k): P50 = %.2f μs%n",
                deleteMetrics.getP50Micros());
        System.out.printf("  Insert latency (1k): P50 = %.2f μs%n",
                insertMetrics.getP50Micros());
        System.out.printf("  Insert degradation: %.1f%%%n",
                Math.abs(insertDegradation));
        System.out.printf("  Delete degradation (5k): %.1f%%%n",
                Math.abs(degradationMetrics.getLatencyDegradationPercent()));

        System.out.println("\nRecall Impact:");
        System.out.printf("  Initial recall: %.4f (baseline)%n", initialRecall);
        System.out.printf("  After 1k deletes: %.4f (%.2f%% drop)%n",
                afterDeleteRecall, recallDrop);
        System.out.printf("  After re-insertion: %.4f (%.2f%% recovery)%n",
                afterInsertRecall, recallRecovery);
        System.out.printf("  After 5k deletes: %.4f (%.2f%% from baseline)%n",
                finalRecall,
                ((finalRecall - initialRecall) / initialRecall) * 100);
    }
}

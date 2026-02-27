import benchmark.*;
import core.Vector;
import core.VectorIndex;
import dataset.RandomVectorGenerator;
import index.flat.FlatIndex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class RandomVectorMain {
    public static void main(String[] args) throws InterruptedException {

        // configuration
        int indexSize = 10000;
        int dimensions = 128;
        int queryCount = 1000;
        int k = 10;
        long seed = 42;

        int vectorsToInsert = 1000;
        int vectorsToDelete = 5000;

        System.out.println("===Dynamic Benchmark: Flat Index===");

        System.out.println("Generating Vectors");
        RandomVectorGenerator generator = new RandomVectorGenerator(seed);
        List<Vector> indexVectors = generator.generate(indexSize,dimensions);
        List<Vector> insertVectors = generator.generate(vectorsToInsert,dimensions);
        List<Vector> queryVectors = generator.generate(queryCount, dimensions);

        System.out.println("Creating flat index...");
        VectorIndex index = new FlatIndex();

        // initial build + query
        System.out.println("===Test 1: Initial Build + Query===");
        Metrics initialMetrics = BenchmarkRunner.run(index,indexVectors,queryVectors,k,"random");
        System.out.println("Build Time: " + initialMetrics.getBuildTimeMs() + " ms");
        System.out.println("Query Latency P50: " + initialMetrics.getQueryLatencyP50Micros() + " μs");
        System.out.println("Throughput: " + initialMetrics.getThroughputQPS() + " QPS");
        System.out.println("Index Size: " + index.size() + " vectors");

        // insert performance
        System.out.println("===Test 2: Insert Performance===");
        InsertMetrics insertMetrics = BenchmarkRunner.benchmarkInserts(index,insertVectors);
        System.out.println("\n" + insertMetrics);
        System.out.println("Index size after inserts: " + index.size() + " vectors");

        // search after inserts
        System.out.println("===Test 3: Search Performance after inserts===");
        Metrics afterInsertMetrics = BenchmarkRunner.measureSearchOnly(index,queryVectors,k,"random");
        System.out.println("Query Latency P50: " + afterInsertMetrics.getQueryLatencyP50Micros() + " μs");
        System.out.println("QPS: " + afterInsertMetrics.getThroughputQPS());

        // calculate degradation from inserts
        double insertDegradation = ((afterInsertMetrics.getQueryLatencyP50Micros() - initialMetrics.getQueryLatencyP50Micros())/ initialMetrics.getQueryLatencyP50Micros()) * 100;
        System.out.printf("Degradation from inserts: %.1f%% %s\n", Math.abs(insertDegradation), insertDegradation > 0 ? "slower" : "faster");

        // delete performance + search degradation
        System.out.println("===Test 4: Deletion Performance and Search Degradation===");
        List<String> idsToDelete = new ArrayList<>();
        for (int i = 0; i < vectorsToDelete; i ++) {
            idsToDelete.add(indexVectors.get(i).id());
        }

        SearchDegradationMetrics degradationMetrics = BenchmarkRunner.benchmarkSearchDegradation(index, queryVectors, k, "random", idsToDelete);
        System.out.println("\n" + degradationMetrics);
        System.out.println("Index size after deletes: " + index.size() + " vectors");

        // delete benchmark
        System.out.println("===Test 5: Delete Performance(1000 more deletes)===");
        List<String> moreIdsToDelete = new ArrayList<>();
        for (int i = vectorsToDelete; i < vectorsToDelete+1000; i++) {
            moreIdsToDelete.add(indexVectors.get(i).id());
        }

        DeleteMetrics deleteMetrics = BenchmarkRunner.benchmarkDeletes(index, moreIdsToDelete);
        System.out.println("\n" + deleteMetrics);
        System.out.println("Final index size: " + index.size() + " vectors");

        System.out.println("\n=== Summary ===");
        System.out.println("Started with: " + indexSize + " vectors");
        System.out.println("Inserted: " + vectorsToInsert + " vectors");
        System.out.println("Deleted: " + (vectorsToDelete + 1000) + " vectors");
        System.out.println("Final size: " + index.size() + " vectors");
        System.out.println("\nPerformance Impact:");
        System.out.printf("  Insert degradation: %.1f%%\n", Math.abs(insertDegradation));
        System.out.printf("  Delete degradation: %.1f%%\n",
                Math.abs(degradationMetrics.getLatencyDegradationPercent()));

    }
}

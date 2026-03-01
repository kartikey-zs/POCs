package benchmark;

import core.QueryResult;
import core.Vector;
import core.VectorIndex;

import java.util.*;

public class BenchmarkRunner {

    public static Metrics run (
            VectorIndex index,
            List<Vector> indexData,
            List<Vector> queryVectors,
            int k,
            String dataset
    ) throws InterruptedException {

        // time the build
        long buildStart = System.currentTimeMillis();
        System.gc();
        Thread.sleep(100);
        index.build(indexData);
        System.gc();
        Thread.sleep(100);
        long buildEnd = System.currentTimeMillis();
        long buildTimeMs = buildEnd - buildStart;

        // capturing the memory after
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long buildMemoryMB = usedMemory / (1024*1024);

        // some warm up to let jvm optimize the code
        for (int i = 0; i < Math.min(100, queryVectors.size()); i ++) {
            index.search(queryVectors.get(i).vector(), k, dataset);
        }

        // query latency measurement
        List<Long> latencies = new ArrayList<>();
        index.resetDistanceCalculations();

        for (Vector queryVector : queryVectors) {
            long start = System.nanoTime();
            index.search(queryVector.vector(),k, dataset);
            long end = System.nanoTime();

            long latencyNanos = end - start;
            latencies.add(latencyNanos);
        }

        Collections.sort(latencies);

        // convert to microseconds and calculate percentiles
        double p50 = latencies.get(latencies.size()/2)/1000.0;
        double p95 = latencies.get((int)(latencies.size()*0.95))/1000.0;
        double p99 = latencies.get((int)(latencies.size()*0.99))/1000.0;

        double averageDist = index.getDistanceCalculations() / (double) queryVectors.size();

        // throughput measurement
        long throughputStart = System.currentTimeMillis();
        for (Vector queryVector : queryVectors) {
            index.search(queryVector.vector(), k, dataset);
        }
        long throughputEnd = System.currentTimeMillis();
        double totalSeconds = (throughputEnd - throughputStart) / 1000.0;
        double qps = queryVectors.size()/totalSeconds;

        return new Metrics(
                buildTimeMs,buildMemoryMB,p50,p95,p99,qps,averageDist
        );
    }

    public static InsertMetrics benchmarkInserts(
            VectorIndex index,
            List<Vector> vectorsToInsert
    ) {
        System.out.println("===Benchmarking Inserts===");
        System.out.println("Inserting " + vectorsToInsert.size() + " vectors...");

        List<Long> latencies = new ArrayList<>();
        long totalStart = System.currentTimeMillis();

        for (Vector vector : vectorsToInsert) {
            long start = System.nanoTime();
            index.insert(vector);
            long end = System.nanoTime();

            latencies.add(end - start);
        }

        long totalEnd = System.currentTimeMillis();

        // calculate percentiles (convert to microseconds)
        Collections.sort(latencies);
        double p50 = latencies.get(latencies.size()/2) / 1000.0;
        double p95 = latencies.get((int)(latencies.size()  * 0.95)) / 1000.0;
        double p99 = latencies.get((int)(latencies.size()  * 0.99)) / 1000.0;

        // calculate throughput
        double totalSeconds = (totalEnd - totalStart) / 1000.0;
        double insertsPerSecond = vectorsToInsert.size()/totalSeconds;

        return new InsertMetrics(
                vectorsToInsert.size(),totalEnd-totalStart, p50,p95,p99, insertsPerSecond
        );
    }

    public static DeleteMetrics benchmarkDeletes (
            VectorIndex index,
            List<String> idsToDelete
    ) {
        System.out.println("===Benchmarking deletes");
        System.out.println("Deleting " + idsToDelete.size() + " vectors...");
        List<Long> latencies = new ArrayList<>();

        long totalStart = System.currentTimeMillis();

        for (String vectorId : idsToDelete) {
            long start = System.nanoTime();
            index.delete(vectorId);
            long end = System.nanoTime();

            latencies.add(end - start);
        }

        long totalEnd = System.currentTimeMillis();

        // calculate percentiles (convert to microseconds)
        Collections.sort(latencies);
        double p50 = latencies.get(latencies.size()/2) / 1000.0;
        double p95 = latencies.get((int)(latencies.size()  * 0.95)) / 1000.0;
        double p99 = latencies.get((int)(latencies.size()  * 0.99)) / 1000.0;

        // calculate throughput
        double totalSeconds = (totalEnd - totalStart) / 1000.0;
        double deletesPerSecond = idsToDelete.size()/totalSeconds;

        return new DeleteMetrics(
                idsToDelete.size(), totalEnd- totalStart, p50, p95, p99, deletesPerSecond
        );
    }

    public static SearchDegradationMetrics benchmarkSearchDegradation(
            VectorIndex index,
            List<Vector> queryVectors,
            int k,
            String dataset,
            List<String> idsToDelete
    ) throws InterruptedException {
        System.out.println("===Benchmarking Search Degradation===");

        // measure search performance before deletions
        System.out.println("Measuring search performance BEFORE deletions...");
        Metrics beforeMetrics = measureSearchOnly(index, queryVectors, k, dataset);

        // delete vectors
        System.out.println("Deleting " + idsToDelete.size() + " vectors...");
        long deleteStart = System.currentTimeMillis();
        for (String id : idsToDelete) {
            index.delete(id);
        }
        long deleteTime = System.currentTimeMillis() - deleteStart;

        // measure search performance after deletions
        System.out.println("Measuring search performance AFTER deletions...");
        Metrics afterMetrics = measureSearchOnly(index, queryVectors, k, dataset);

        return new SearchDegradationMetrics(
                beforeMetrics,afterMetrics,idsToDelete.size(),deleteTime
        );
    }

    public static Metrics measureSearchOnly(VectorIndex index, List<Vector> queryVectors, int k, String dataset) {
        for (int i = 0; i < Math.min(10, queryVectors.size()); i++) {
            index.search(queryVectors.get(i).vector(), k , dataset);
        }

        // measure latency
        List<Long> latencies = new ArrayList<>();
        index.resetDistanceCalculations();

        for (Vector queryVector : queryVectors) {
            long start = System.nanoTime();
            index.search(queryVector.vector(), k, dataset);
            long end = System.nanoTime();
            latencies.add(end - start);
        }

        Collections.sort(latencies);
        double p50 = latencies.get(latencies.size()/2) / 1000.0;
        double p95 = latencies.get((int)(latencies.size()  * 0.95)) / 1000.0;
        double p99 = latencies.get((int)(latencies.size()  * 0.99)) / 1000.0;

        double avgDistance = index.getDistanceCalculations() / (double) queryVectors.size();

        // measure throughput
        long throughputStart = System.currentTimeMillis();
        for (Vector queryVector : queryVectors) {
            index.search(queryVector.vector(), k , dataset);
        }
        long throughputEnd = System.currentTimeMillis();
        double totalSeconds = (throughputEnd - throughputStart) / 1000.0;
        double qps = queryVectors.size() / totalSeconds;

        return new Metrics(0,0,p50,p95,p99,qps,avgDistance);
    }

    public static double calculateRecall(List<QueryResult> results, int[] groundTruth, int k) {
        Set<String> resultIds = new HashSet<>();
        for (int i = 0; i < Math.min(k, results.size()); i ++) {
            resultIds.add(results.get(i).getId());
        }

        // count matches with the ground truth (first k entries)
        int matches = 0;
        for (int i = 0; i < Math.min(k, groundTruth.length); i++) {
            String groundTruthId = "cohere_" + groundTruth[i];
            if (resultIds.contains(groundTruthId)) {
                matches++;
            }
        }
        return (double) matches/k;
    }

    public static int calculateRecallCount(List<QueryResult> results, List<Integer> filteredGT, int k) {
        Set<String> resultIds = new HashSet<>();
        for (int i = 0; i < Math.min(k, results.size()); i++) {
            resultIds.add(results.get(i).getId());
        }
        int matches = 0;
        for (int gtId : filteredGT) {
            if (resultIds.contains("cohere_" + gtId)) {
                matches++;
            }
        }
        return matches;
    }
}

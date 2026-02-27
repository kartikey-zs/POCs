import benchmark.BenchmarkRunner;
import benchmark.Metrics;
import core.QueryResult;
import core.Vector;
import core.VectorIndex;
import dataset.DatasetLoader;
import index.ivf.IVFIndex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SiftMain {
    public static void main(String[] args) throws IOException, InterruptedException {

        int k = 10;
        List<Vector> indexVectors = DatasetLoader.loadFVectors("/Users/kartikeysrivastava/Desktop/projects/dataset/siftsmall-10k/siftsmall_base.fvecs");
        List<Vector> queryVectors = DatasetLoader.loadFVectors("/Users/kartikeysrivastava/Desktop/projects/dataset/siftsmall-10k/siftsmall_query.fvecs");
        List<int []> groundTruthBig = DatasetLoader.loadIVecs("/Users/kartikeysrivastava/Desktop/projects/dataset/siftsmall-10k/siftsmall_groundtruth.ivecs");

        VectorIndex index = new IVFIndex(50,5 );
        System.out.println("Creating " + index.getName() +  " index on a " + indexVectors.size() + " dataset");

        System.out.println("Running benchmark using : " + index.getName() + " index" );
        Metrics metrics = BenchmarkRunner.run(index, indexVectors, queryVectors, k, "sift");

        System.out.println("\n=== Benchmark Results ===");
        System.out.println("Build Time: " + metrics.getBuildTimeMs() + " ms");
        System.out.println("Build Memory: " + metrics.getBuildMemoryMB() + " MB");
        System.out.println("Query Latency P50: " + metrics.getQueryLatencyP50Micros() + " μs");
        System.out.println("Query Latency P95: " + metrics.getQueryLatencyP95Micros() + " μs");
        System.out.println("Query Latency P99: " + metrics.getQueryLatencyP99Micros() + " μs");
        System.out.println("Throughput: " + metrics.getThroughputQPS() + " QPS");
        System.out.println("Avg Distance Calculations: " + metrics.getAvgDistanceCalculations());

        List<Double> recalls = new ArrayList<>();
        for (int i = 0; i < queryVectors.size(); i++) {
            List<QueryResult> results = index.search(queryVectors.get(i).vector(), 10, "sift");
            double recall = BenchmarkRunner.calculateRecall(results, groundTruthBig.get(i), 10);
            recalls.add(recall);
        }

        // calculate stats
        double avgRecall = recalls.stream().mapToDouble(d->d).average().orElse(0.0);
        double minRecall = recalls.stream().mapToDouble(d->d).min().orElse(0.0);
        double maxRecall = recalls.stream().mapToDouble(d->d).max().orElse(0.0);

        System.out.println("Recall@10 - Avg: " + avgRecall + ", Min: " + minRecall + ", Max: " + maxRecall);
    }
}

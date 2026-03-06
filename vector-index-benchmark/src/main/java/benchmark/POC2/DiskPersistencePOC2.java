package benchmark.POC2;

import core.Vector;
import dataset.DatasetLoader;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class DiskPersistencePOC2 {

    private static final VectorTypeSupport vts = VectorizationProvider.getInstance().getVectorTypeSupport();

    // =======================
    // Config
    // =======================
    private static final String DATASET_PATH   = "/Users/kartikeysrivastava/Desktop/projects/dataset/sift-1M/";
    private static final String BASE_VECTORS   = DATASET_PATH + "sift_base.fvecs";
    private static final String QUERY_VECTORS  = DATASET_PATH + "sift_query.fvecs";
    private static final String GROUND_TRUTH   = DATASET_PATH + "sift_groundtruth.ivecs";

    private static final Path   GRAPH_PATH      = Paths.get(DATASET_PATH + "sift_index.bin");
    private static final Path   PQ_PATH         = Paths.get(DATASET_PATH + "sift_pq.bin");
    private static final Path   CHECKPOINT_PATH = Paths.get(DATASET_PATH + "sift_checkpoint.bin");

    private static final int M               = 16;
    private static final int EF_CONSTRUCTION = 100;
    private static final int EF_SEARCH       = 200;
    private static final int K               = 10;
    private static final int PQ_SUBSPACES    = 16; // 16 subspaces x 8 dims = 128 dims
    private static final int WARMUP_COUNT    = 1000;
    private static final String datasetPrefix = "sift_";
    private static final String dataset = "sift";

    public static void main(String[] args) throws Exception {

        // =======================
        // Load Dataset
        // =======================
        System.out.println("Loading SIFT-1M dataset...");
        List<Vector> indexVectors = DatasetLoader.loadFVectors(BASE_VECTORS, datasetPrefix);
        List<Vector> queryVectors = DatasetLoader.loadFVectors(QUERY_VECTORS, datasetPrefix);
        List<int[]>  groundTruth  = DatasetLoader.loadIVecs(GROUND_TRUTH);
        int dimension = indexVectors.get(0).dimensions();
        System.out.printf("Loaded %d index vectors, %d query vectors, dim=%d%n",
                indexVectors.size(), queryVectors.size(), dimension);

        // Convert query vectors to VectorFloat for on-disk search
        List<VectorFloat<?>> queryJVectors = toJVectorList(queryVectors);

        // =======================
        // Phase 1: Build
        // =======================
        System.out.println("\n=== Phase 1: Build ===");
        var build = DiskPersistenceBenchmark.buildIndex(
                indexVectors, dimension, M, EF_CONSTRUCTION, PQ_SUBSPACES);
        System.out.printf("Graph build time     : %d ms%n",  build.buildTimeMs());
        System.out.printf("PQ train time        : %d ms%n",  build.pqTrainTimeMs());
        System.out.printf("PQ encode time       : %d ms%n",  build.pqEncodeTimeMs());

        Files.deleteIfExists(GRAPH_PATH);
        Files.deleteIfExists(PQ_PATH);
        Files.deleteIfExists(CHECKPOINT_PATH);

        // =======================
        // Phase 2: Write
        // =======================
        System.out.println("\n=== Phase 2: Write ===");
        var write = DiskPersistenceBenchmark.writeIndex(
                build.graph(), build.jvectorVectors(), dimension, build.pqVectors(),
                GRAPH_PATH, PQ_PATH);
        System.out.printf("Graph write time     : %d ms%n",    write.graphWriteTimeMs());
        System.out.printf("Graph file size      : %.2f MB%n",  write.graphFileSizeBytes() / 1_048_576.0);
        System.out.printf("PQ write time        : %d ms%n",    write.pqWriteTimeMs());
        System.out.printf("PQ file size         : %.2f MB%n",  write.pqFileSizeBytes() / 1_048_576.0);

        // =======================
        // Phase 3: Load
        // =======================
        System.out.println("\n=== Phase 3: Load ===");
        var load = DiskPersistenceBenchmark.loadIndex(GRAPH_PATH, PQ_PATH);
        System.out.printf("Load time            : %d ms%n", load.loadTimeMs());

        try {
            // =======================
            // Phase 4: Memory
            // =======================
            System.out.println("\n=== Phase 6: Memory Footprint ===");
            var memory = DiskPersistenceBenchmark.measureMemory(
                    load.index(), load.pqVectors(), build.graph(),
                    queryJVectors.get(0), K, EF_SEARCH);
            System.out.printf("OnDiskGraphIndex RAM : %.2f MB%n",  memory.onDiskIndexBytes()       / 1_048_576.0);
            System.out.printf("PQVectors RAM        : %.2f MB%n",  memory.pqVectorsBytes()         / 1_048_576.0);
            System.out.printf("Total on-disk mode   : %.2f MB%n",  memory.totalOnDiskModeBytes()   / 1_048_576.0);
            System.out.printf("OnHeapGraphIndex RAM : %.2f MB%n",  memory.onHeapBytes()            / 1_048_576.0);

            // =======================
            // Phase 5: Cold Start
            // =======================
            System.out.println("\n=== Phase 4: Cold Start (first query, no warmup) ===");
            var cold = DiskPersistenceBenchmark.measureColdStart(
                    load.index(), queryJVectors.get(0), K, EF_SEARCH);
            System.out.printf("First query latency  : %.2f μs%n", cold.firstQueryMicros());

            // =======================
            // Phase 6: Steady State
            // =======================
            System.out.println("\n=== Phase 5: Steady State (after " + WARMUP_COUNT + " warmup queries) ===");
            var steady = DiskPersistenceBenchmark.measureSteadyState(
                    load.index(), queryJVectors, groundTruth, K, EF_SEARCH, WARMUP_COUNT);
            System.out.printf("Latency P50          : %.2f μs%n",  steady.p50Micros());
            System.out.printf("Latency P95          : %.2f μs%n",  steady.p95Micros());
            System.out.printf("Latency P99          : %.2f μs%n",  steady.p99Micros());
            System.out.printf("Avg Recall@%d        : %.4f%n",  K, steady.avgRecall());
        } finally {
            // Always close the reader supplier to release the mmap
            load.readerSupplier().close();
        }

        // =======================
        // Phase 7: Checkpoint-Resume
        // =======================
        System.out.println("\n=== Phase 7: Checkpoint-Resume ===");
        var checkpoint = DiskPersistenceBenchmark.benchmarkCheckpointResume(
                build.jvectorVectors(), dimension, M, EF_CONSTRUCTION, CHECKPOINT_PATH);
        System.out.printf("Save time            : %d ms%n",  checkpoint.saveTimeMs());
        System.out.printf("Resume + finish time : %d ms%n",  checkpoint.resumeAndFinishTimeMs());
        System.out.printf("Full rebuild time    : %d ms%n",  checkpoint.fullRebuildTimeMs());
        System.out.printf("Time saved by resume : %d ms%n",
                checkpoint.fullRebuildTimeMs() - checkpoint.resumeAndFinishTimeMs());
    }

    // =======================
    // Helpers
    // =======================

    private static List<VectorFloat<?>> toJVectorList(List<Vector> vectors) {
        List<VectorFloat<?>> result = new ArrayList<>(vectors.size());
        for (Vector v : vectors) {
            VectorFloat<?> vf = vts.createFloatVector(v.dimensions());
            for (int i = 0; i < v.dimensions(); i++) {
                vf.set(i, v.vector()[i]);
            }
            result.add(vf);
        }
        return result;
    }
}
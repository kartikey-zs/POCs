package index.hnsw;

import benchmark.BenchmarkExecutors;
import com.github.jelmerk.hnswlib.core.DistanceFunction;
import com.github.jelmerk.hnswlib.core.SearchResult;
import com.github.jelmerk.hnswlib.core.hnsw.HnswIndex;
import core.DistanceMetric;
import core.QueryResult;
import core.Vector;
import core.VectorIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class JelmarkHNSWIndex implements VectorIndex {
    private final int m;
    private final int efConstruction;
    private final int efSearch;
    private HnswIndex<String, float[], Vector, Float> index;
    private long distanceCalculations = 0;
    private final DistanceFunction<float[], Float> distanceFunction;
    private final AtomicLong versionCounter = new AtomicLong(0);
    private final ExecutorService insertExecutor;

    // constructor with executor service
    public JelmarkHNSWIndex(int m, int efConstruction, int efSearch, ExecutorService insertExecutor) {
        this.m = m;
        this.efConstruction = efConstruction;
        this.efSearch = efSearch;
        this.insertExecutor = insertExecutor;

        this.distanceFunction = (vector1, vector2) -> {
            distanceCalculations++;
            return new DistanceMetric().euclideanDistance(vector1, vector2);
        };
    }

    public JelmarkHNSWIndex(int m, int efConstruction, int efSearch) {
        this(m, efConstruction, efSearch, null);
    }
    @Override
    public void build(List<Vector> vectors) {
        System.out.println("Creating HNSW index with M=" + m + ", efConstruction=" + efConstruction + ", efSearch=" + efSearch);
        System.out.println("Dataset size: " + vectors.size() + " vectors");

        long startTime = System.currentTimeMillis();
        int maxCapacity = (int)(vectors.size() * 1.2);

        // Create HNSW index
        this.index = HnswIndex
                .newBuilder(vectors.get(0).dimensions(), distanceFunction, maxCapacity)
                .withM(m)
                .withEfConstruction(efConstruction)
                .withEf(efSearch)
                .withRemoveEnabled()
                .build();

        System.out.println("Index structure created, now adding vectors...");

        // Add all vectors to index with progress tracking
        for (Vector vector : vectors) {
            index.add(vector);
        }

        long totalTime = System.currentTimeMillis() - startTime;
        System.out.printf("Build completed in %.2fs\n", totalTime / 1000.0);
    }

    @Override
    public int size() {
        return index.size();
    }

    @Override
    public List<QueryResult> search(float[] query, int k, String dataset) {

        Vector queryVector = new Vector("query", query);
        List<SearchResult<Vector, Float>> results =
                index.findNearest(queryVector.vector(), k);

        List<QueryResult> searchResults = new ArrayList<>();
        for (SearchResult<Vector, Float> result : results) {
            searchResults.add(new QueryResult(result.item().id(), result.distance()));
        }

        return searchResults;
    }

    @Override
    public long getDistanceCalculations() {
        return distanceCalculations;
    }

    @Override
    public void resetDistanceCalculations() {
        this.distanceCalculations = 0;
    }

    @Override
    public String getName() {
        return "JelMark-HNSW";
    }

    @Override
    public void insert(Vector vector) {
        long version = versionCounter.incrementAndGet();
        Vector versionedVector = new Vector(vector.id(), vector.vector(), version);
        index.add(versionedVector);
    }

    @Override
    public void delete(String vectorId) {
        long version = versionCounter.incrementAndGet();
        index.remove(vectorId,version);
    }

    @Override
    public void insertAsync(List<Vector> vectors) {
        if (insertExecutor == null) {
            for (Vector v : vectors) {
                insert(v);
            }
        }

        // parallel insertion
        List<CompletableFuture<Void>> futures = vectors.stream()
                .map(v -> CompletableFuture.runAsync(() -> {
                    long version = versionCounter.incrementAndGet();
                    Vector versionedVector = new Vector(v.id(), v.vector(), version);
                    index.add(versionedVector);
                }, insertExecutor))
                .toList();

        // wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    @Override
    public CompletableFuture<List<QueryResult>> searchAsync(float[] query, int k, String dataset) {
        return null;
    }
}

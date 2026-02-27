package index.hnsw;

import core.QueryResult;
import core.Vector;
import core.VectorIndex;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.jbellis.jvector.graph.*;
import io.github.jbellis.jvector.graph.similarity.BuildScoreProvider;
import io.github.jbellis.jvector.graph.similarity.SearchScoreProvider;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;

public class JVectorHNSWIndex implements VectorIndex {
    private static final VectorTypeSupport vts = VectorizationProvider.getInstance().getVectorTypeSupport();
    private final int m;
    private final int efConstruction;
    private int efSearch;

    private GraphIndexBuilder builder;
    private ArrayList<Vector> vectors;
    private ArrayList<VectorFloat<?>> jvectorVectors;
    private HashMap<String, Integer> idToNodeMap;

    private RandomAccessVectorValues ravv;
    private BuildScoreProvider bsp;
    private int dimension;
    private long distanceCalculations = 0;

    private final AtomicInteger nextNodeId = new AtomicInteger(0);
    private final AtomicInteger softDeleteCount = new AtomicInteger(0);
    private final AtomicInteger liveNodeCount = new AtomicInteger(0);
    private final ExecutorService insertExecutor;

    public JVectorHNSWIndex(int m, int efConstruction, int efSearch, ExecutorService insertExecutor) {
        this.m = m;
        this.efConstruction = efConstruction;
        this.efSearch = efSearch;
        this.insertExecutor = insertExecutor;
    }

    public JVectorHNSWIndex(int m, int efConstruction, int efSearch) {
        this(m, efConstruction, efSearch, null);
    }

    @Override
    public void build(List<Vector> vectors) {
        System.out.println("Creating JVector HNSW index with M=" + m + ", efConstruction=" + efConstruction + ", efSearch=" + efSearch);
        System.out.println("Dataset size: " + vectors.size() + " vectors");

        this.vectors = new ArrayList<>(vectors);
        this.dimension = vectors.get(0).dimensions();
        long startTime = System.currentTimeMillis();

        // convert to vector float list
        this.jvectorVectors = new ArrayList<>();
        this.idToNodeMap = new HashMap<>();
        for (int i = 0; i < vectors.size(); i++) {
            Vector v = vectors.get(i);
            VectorFloat<?> vf = vts.createFloatVector(v.dimensions());
            for (int j = 0; j < v.dimensions(); j++) {
                vf.set(j, v.vector()[j]);
            }
            jvectorVectors.add(vf);
            idToNodeMap.put(v.id(), i);
        }

        // create ravv
        int dimension = vectors.get(0).dimensions();
        this.ravv = new ListRandomAccessVectorValues(jvectorVectors, dimension);

        // build score provider
        this.bsp = BuildScoreProvider.randomAccessScoreProvider(ravv, VectorSimilarityFunction.EUCLIDEAN);

        System.out.println("Index structure created, now adding vectors...");

        // build the graph
        this.builder = new GraphIndexBuilder(
                bsp,
                dimension,
                m,
                efConstruction,
                1.2f,
                1.2f,
                true,
                false
        );
        builder.build(ravv);

        // Initialize counters after build
        this.nextNodeId.set(vectors.size());
        this.liveNodeCount.set(builder.getGraph().size(0));

        long totalTime = System.currentTimeMillis() - startTime;
        System.out.printf("Build completed in %.2fs\n", totalTime / 1000.0);
    }

    @Override
    public int size() {
        return liveNodeCount.get();
    }

    @Override
    public List<QueryResult> search(float[] query, int k, String dataset) {
        // convert query to vector float
        VectorFloat<?> queryVector = vts.createFloatVector(query.length);
        for (int i = 0; i < query.length; i++) {
            queryVector.set(i, query[i]);
        }

        try (GraphSearcher searcher = new GraphSearcher(builder.getGraph())) {
            SearchScoreProvider ssp = bsp.searchProviderFor(queryVector);
            SearchResult result = searcher.search(ssp, k, efSearch, 0.0F, 0.0F, builder.getGraph().getView().liveNodes());

            // convert to our format
            List<QueryResult> results = new ArrayList<>();
            for (SearchResult.NodeScore ns : result.getNodes()) {
                String id = vectors.get(ns.node).id();
                results.add(new QueryResult(id, ns.score));
            }
            return results;
        } catch (IOException e) {
            throw new RuntimeException("Search failed", e);
        }
    }

    @Override
    public long getDistanceCalculations() {
        return 0;
    }

    @Override
    public void resetDistanceCalculations() {
        this.distanceCalculations = 0;
    }

    @Override
    public String getName() {
        return "JVector-HNSW";
    }

    /**
     * Insert a single vector.
     * WARNING: This method is NOT thread-safe for concurrent calls.
     * For concurrent insertions, use insertAsync() with an executor.
     */
    @Override
    public void insert(Vector vector) {
        VectorFloat<?> vf = vts.createFloatVector(vector.dimensions());
        for (int i = 0; i < vector.dimensions(); i++) {
            vf.set(i, vector.vector()[i]);
        }

        // Thread-safe operations with concurrent collections
        int nodeId = nextNodeId.getAndIncrement();
        vectors.add(vector);
        jvectorVectors.add(vf);
        idToNodeMap.put(vector.id(), nodeId);
        builder.addGraphNode(nodeId, vf);
        liveNodeCount.incrementAndGet();
    }

    // record to hold insertion data
    private record InsertTask(int nodeId, VectorFloat<?> vf) {}

    @Override
    public void insertAsync(List<Vector> vectors) {
        if (insertExecutor == null) {
            // Sequential fallback
            for (Vector v : vectors) {
                insert(v);
            }
            return;
        }

        // sequential preparation
        List<InsertTask> tasks = new ArrayList<>(vectors.size());
        for (Vector v : vectors) {
            VectorFloat<?> vf = vts.createFloatVector(v.dimensions());
            for (int i = 0; i < v.dimensions(); i++) {
                vf.set(i,v.vector()[i]);
            }

            // assign node id and update data structures
            int nodeId = nextNodeId.getAndIncrement();
            this.vectors.add(v);
            this.jvectorVectors.add(vf);
            idToNodeMap.put(v.id(),nodeId);
            liveNodeCount.incrementAndGet();
            tasks.add(new InsertTask(nodeId,vf));
        }
        // parallel graph insertion
        List<CompletableFuture<Void>> futures = tasks.stream()
                .map(task -> CompletableFuture.runAsync(() -> builder.addGraphNode(task.nodeId, task.vf), insertExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    @Override
    public CompletableFuture<List<QueryResult>> searchAsync(float[] query, int k, String dataset) {
        if (insertExecutor == null) {
            // Fallback to synchronous execution wrapped in completed future
            return CompletableFuture.completedFuture(search(query, k, dataset));
        }
        return CompletableFuture.supplyAsync(() -> search(query, k, dataset), insertExecutor);
    }

    @Override
    public void delete(String vectorId) {
        Integer nodeId = idToNodeMap.get(vectorId);
        if (nodeId == null) return;

        builder.markNodeDeleted(nodeId);
        softDeleteCount.incrementAndGet();
        liveNodeCount.decrementAndGet();

        if (softDeleteCount.get() > 5000) {
            cleanup();
        }
    }

    /**
     * Cleanup deleted nodes - blocking compaction operation.
     * Call periodically when delete percentage gets too high.
     */
    public long cleanup() {
        if (softDeleteCount.get() == 0) {
            System.out.println("No deleted nodes to cleanup");
            return 0;
        }

        long startTime = System.currentTimeMillis();
        long freedMemory = builder.removeDeletedNodes();

        softDeleteCount.set(0);
        liveNodeCount.set(builder.getGraph().size(0));

        return freedMemory;
    }
}

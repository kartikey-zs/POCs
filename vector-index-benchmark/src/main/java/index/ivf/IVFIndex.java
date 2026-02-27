package index.ivf;

import core.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class IVFIndex implements VectorIndex {
    private final int nList;
    private final int nProbe;

    private KMeans kMeans;
    private List<List<Vector>> invertedLists;
    private long distanceCalculations = 0;

    public IVFIndex(int nList, int nProbe) {
        this.nList = nList;
        this.nProbe = nProbe;
    }
    @Override
    public void build(List<Vector> vectors) {
        System.out.println("Building IVF index with nList = " + nList + " and nProbe = " + nProbe);
        System.out.println("Dataset size: " + vectors.size() + " vectors");

        long startTime = System.currentTimeMillis();
        // run k-means clustering
        kMeans = new KMeans(nList, 20);
        kMeans.fit(vectors);

        // initialize inverted lists
        invertedLists = new ArrayList<>();
        for (int i = 0; i < nList; i++) {
            invertedLists.add(new ArrayList<>());
        }

        // assign each vector to its nearest cluster
        System.out.println("Populating inverted lists...");
        for (Vector v : vectors) {
            int clusterId = kMeans.findNearestCentroid(v.vector());
            invertedLists.get(clusterId).add(v);
        }

        // print cluster stats
        printClusterStatistics();
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.printf("IVF index built in %.2fs\n", totalTime/1000.0);
    }

    @Override
    public int size() {
        int total = 0;
        for (List<Vector> list : invertedLists) {
            total += list.size();
        }
        return total;
    }

    @Override
    public List<QueryResult> search(float[] query, int k, String dataset) {

        // find nProbe nearest centroids (coarse search)
        List<Integer> nearestCluster = kMeans.findNearestCentroids(query,nProbe);
        distanceCalculations+=nList;

        // collect all candidates from selected clusters
        List<QueryResult> candidates = new ArrayList<>();
        for (int clusterId : nearestCluster) {
            List<Vector> vectorsInCluster = invertedLists.get(clusterId);
            // brute force
            for (Vector v : vectorsInCluster) {
                float distance = new DistanceMetric().euclideanDistance(query, v.vector());
                candidates.add(new QueryResult(v.id(),distance));
                distanceCalculations++;
            }
        }
        // sort candidates by distance and return top k
        Collections.sort(candidates);

        int returnSize = Math.min(k, candidates.size());
        return candidates.subList(0,returnSize);
    }

    @Override
    public long getDistanceCalculations() {
        return distanceCalculations;
    }

    @Override
    public void resetDistanceCalculations() {
        distanceCalculations = 0;
    }

    @Override
    public String getName() {
        return "IVF Index";
    }

    @Override
    public void insert(Vector vector) {

    }

    @Override
    public void delete(String vectorId) {

    }

    @Override
    public void insertAsync(List<Vector> vectors) {

    }

    @Override
    public CompletableFuture<List<QueryResult>> searchAsync(float[] query, int k, String dataset) {
        return null;
    }

    private void printClusterStatistics() {
        int minSize = Integer.MAX_VALUE;
        int maxSize = 0;
        int emptyCount = 0;

        for (List<Vector> list : invertedLists) {
            int size = list.size();
            if (size == 0) {
                emptyCount++;
            }
            minSize = Math.min(minSize,size);
            maxSize = Math.max(maxSize,size);
        }

        double avgSize = (double) size() / nList;

        System.out.println("Cluster statistics");
        System.out.printf(" Average cluster size: %.1f\n", avgSize);
        System.out.printf(" Min cluster size: %d\n", minSize);
        System.out.printf(" Max cluster size: %d\n", maxSize);
        System.out.printf(" Empty clusters: %d / %d\n", emptyCount, nList);
    }
}

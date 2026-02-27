package core;

import utils.CentroidDistance;

import java.util.*;

public class KMeans {
    private final int nClusters;
    private final int maxIterations;
    private List<Vector> centroids;
    private final Random random;
    private final int dimension;

    public KMeans(int nClusters, int maxIterations) {
        this.nClusters = nClusters;
        this.maxIterations = maxIterations;
        this.random = new Random(42);
        this.dimension = 128;
    }

    // run k-means clustering on the dataset
    public void fit(List<Vector> data) {
        System.out.println("Running k-means with " + nClusters + " clusters");

        // initialize centroids randomly
        initializeCentroids(data);

        // iterate to refine cluster
        for (int i = 0; i < maxIterations; i++) {
            // assign each vector to nearest centroid
            List<List<Vector>> clusters = assignToCluster(data);

            // update centroids (mean of each cluster)
            boolean changed = updateCentroids(clusters);

            // break early if converged
            if (!changed && i > 5) {
                System.out.println("Converged at iteration " + i);
                break;
            }

            if ((i+1) % 5 == 0) {
                System.out.println("K-means iteration " + (i+1)+"/" + maxIterations);
            }
        }
        System.out.println("K-means clustering complete");
    }

    // initialize centroids by randomly selecting vectors from dataset
    private void initializeCentroids(List<Vector> data) {
        centroids = new ArrayList<>(nClusters);

        // randomly select nClusters vectors as initial centroids
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            indices.add(i);
        }
        Collections.shuffle(indices, random);

        for (int i = 0; i < nClusters; i++) {
            Vector original = data.get(indices.get(i));
            // create a copy of vector
            float[] centroidData = Arrays.copyOf(original.vector(), original.dimensions());
            centroids.add(new Vector("centroid_" + i, centroidData));
        }
    }

    // assign each vector to its nearest centroid
    private List<List<Vector>> assignToCluster(List<Vector> data) {
        // initialize empty clusters
        List<List<Vector>> clusters = new ArrayList<>(nClusters);
        for (int i = 0; i < nClusters; i++) {
            clusters.add(new ArrayList<>());
        }

        // assign each vector to the nearest centroid
        for (Vector v : data) {
            int nearestCluster = findNearestCentroid(v.vector());
            clusters.get(nearestCluster).add(v);
        }
        return clusters;
    }

    // update centroids to be the mean of their assigned vectors
    // returns true if any centroid changed significantly
    private boolean updateCentroids(List<List<Vector>> clusters) {
        boolean changed = false;

        for (int i = 0; i < nClusters; i++) {
            List<Vector> cluster = clusters.get(i);

            // skip empty cluster
            if (cluster.isEmpty()) continue;

            // calculate mean of all vectors in a cluster
            float [] newCentroid = new float[dimension];

            for (Vector v : cluster) {
                for (int d = 0; d < dimension; d++) {
                    newCentroid[d] += v.vector()[d];
                }
            }

            for (int d = 0; d < dimension; d++) {
                newCentroid[d] /= cluster.size();
            }

            //check if centroid changed significantly
            float distance = new DistanceMetric().euclideanDistance(centroids.get(i).vector(), newCentroid);
            if (distance > 0.01f) {
                changed = true;
            }

            // update centroid
            centroids.set(i, new Vector("centroid_" + i, newCentroid));
        }
        return changed;
    }

    // find the nearest centroid (used during build)
    public int findNearestCentroid(float[] vector) {
        int nearest = 0;
        float minDistance = new DistanceMetric().euclideanDistance(vector, centroids.get(0).vector());

        for (int i = 1; i < nClusters; i++) {
            float distance = new DistanceMetric().euclideanDistance(vector, centroids.get(i).vector());
            if (distance < minDistance) {
                minDistance = distance;
                nearest = i;
            }
        }
        return nearest;
    }

    // find nProbe nearest centroids(used during search)
    public List<Integer> findNearestCentroids(float [] query, int nProbe) {
        List<CentroidDistance> distances = new ArrayList<>();
        for (int i = 0; i < nClusters; i++) {
            float distance = new DistanceMetric().euclideanDistance(query, centroids.get(i).vector());
            distances.add(new CentroidDistance(i, distance));
        }
        Collections.sort(distances);

        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < Math.min(nProbe, distances.size()); i ++) {
            result.add(distances.get(i).getClusterId());
        }
        return result;
    }

    public List<Vector> getCentroids() {
        return centroids;
    }

    public int getNClusters() {
        return nClusters;
    }

}

package core;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface VectorIndex {
    void build(List<Vector> vectors);
    int size();
    List<QueryResult> search(float[] query, int k, String dataset);
    long getDistanceCalculations();
    void resetDistanceCalculations();
    String getName();
    void insert(Vector vector);
    void delete(String vectorId);

    void insertAsync(List<Vector> vectors);
    CompletableFuture<List<QueryResult>> searchAsync(float[] query, int k, String dataset);}

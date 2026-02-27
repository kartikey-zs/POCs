package core;

public class QueryResult implements Comparable<QueryResult> {
    String id;
    float distance;

    public QueryResult(String id, float distance) {
        this.id = id;
        this.distance = distance;
    }

    public String getId() {
        return id;
    }

    public float getDistance() {
        return distance;
    }

    @Override
    public int compareTo(QueryResult otherDistance) {
        return Float.compare(this.distance, otherDistance.distance);
    }
}

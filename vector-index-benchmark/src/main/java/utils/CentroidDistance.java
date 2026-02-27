package utils;

public class CentroidDistance implements Comparable<CentroidDistance> {
    int clusterId;
    float distance;

    public CentroidDistance(int clusterId, float distance) {
        this.clusterId = clusterId;
        this.distance = distance;
    }

    public int getClusterId() {
        return clusterId;
    }

    public float getDistance() {
        return distance;
    }

    @Override
    public int compareTo(CentroidDistance o) {
        return Float.compare(this.distance, o.distance);
    }
}

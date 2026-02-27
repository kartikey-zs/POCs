package benchmark;

public class Metrics {
    long buildTimeMs;
    long buildMemoryMB;
    double queryLatencyP50Micros;
    double queryLatencyP95Micros;
    double queryLatencyP99Micros;
    double throughputQPS;
    double avgDistanceCalculations; // every query checks how many vectors on an average

    public Metrics(
            long buildTimeMs, long buildMemoryMB,
            double queryLatencyP50Micros, double queryLatencyP95Micros,
            double queryLatencyP99Micros, double throughputQPS,
            double avgDistanceCalculations
    ) {
        this.buildTimeMs = buildTimeMs;
        this.buildMemoryMB = buildMemoryMB;
        this.queryLatencyP50Micros = queryLatencyP50Micros;
        this.queryLatencyP95Micros = queryLatencyP95Micros;
        this.queryLatencyP99Micros = queryLatencyP99Micros;
        this.throughputQPS = throughputQPS;
        this.avgDistanceCalculations = avgDistanceCalculations;
    }

    public long getBuildTimeMs() {
        return buildTimeMs;
    }

    public long getBuildMemoryMB() {
        return buildMemoryMB;
    }

    public double getQueryLatencyP50Micros() {
        return queryLatencyP50Micros;
    }

    public double getQueryLatencyP95Micros() {
        return queryLatencyP95Micros;
    }

    public double getQueryLatencyP99Micros() {
        return queryLatencyP99Micros;
    }

    public double getThroughputQPS() {
        return throughputQPS;
    }

    public double getAvgDistanceCalculations() {
        return avgDistanceCalculations;
    }

    @Override
    public String toString() {
        return "Metrics{" +
                "buildTimeMs=" + buildTimeMs +
                ", buildMemoryMB=" + buildMemoryMB +
                ", queryLatencyP50Micros=" + queryLatencyP50Micros +
                ", queryLatencyP95Micros=" + queryLatencyP95Micros +
                ", queryLatencyP99Micros=" + queryLatencyP99Micros +
                ", throughputQPS=" + throughputQPS +
                ", avgDistanceCalculations=" + avgDistanceCalculations +
                '}';
    }
}

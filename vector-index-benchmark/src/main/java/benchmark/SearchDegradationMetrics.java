package benchmark;

public class SearchDegradationMetrics {
    private final Metrics beforeDeletion;
    private final Metrics afterDeletion;
    private final int vectorsDeleted;
    private final long deleteTimeMs;

    public SearchDegradationMetrics(Metrics beforeDeletion, Metrics afterDeletion, int vectorDeleted, long deleteTimeMs) {
        this.beforeDeletion = beforeDeletion;
        this.afterDeletion = afterDeletion;
        this.vectorsDeleted = vectorDeleted;
        this.deleteTimeMs = deleteTimeMs;
    }

    public Metrics getBeforeDeletion() {
        return beforeDeletion;
    }

    public Metrics getAfterDeletion() {
        return afterDeletion;
    }

    public int getVectorsDeleted() {
        return vectorsDeleted;
    }

    public long getDeleteTimeMs() {
        return deleteTimeMs;
    }

    public double getLatencyDegradationPercent() {
        return ((afterDeletion.getQueryLatencyP50Micros() -
                beforeDeletion.getQueryLatencyP50Micros())/
                beforeDeletion.getQueryLatencyP50Micros())
                * 100;
    }

    @Override
    public String toString() {
        return String.format(
                """
                        === Search Degradation After Deleting %d Vectors ===

                        BEFORE DELETION:
                          P50 Latency: %.2f μs
                          P95 Latency: %.2f μs
                          QPS: %.2f
                          Avg Dist Calcs: %.2f

                        DELETION:
                          Time: %d ms

                        AFTER DELETION:
                          P50 Latency: %.2f μs (%.1f%% %s)
                          P95 Latency: %.2f μs
                          QPS: %.2f
                          Avg Dist Calcs: %.2f""",
                vectorsDeleted,
                beforeDeletion.getQueryLatencyP50Micros(),
                beforeDeletion.getQueryLatencyP95Micros(),
                beforeDeletion.getThroughputQPS(),
                beforeDeletion.getAvgDistanceCalculations(),
                deleteTimeMs,
                afterDeletion.getQueryLatencyP50Micros(),
                Math.abs(getLatencyDegradationPercent()),
                getLatencyDegradationPercent() > 0 ? "slower" : "faster",
                afterDeletion.getQueryLatencyP95Micros(),
                afterDeletion.getThroughputQPS(),
                afterDeletion.getAvgDistanceCalculations()
        );
    }
}

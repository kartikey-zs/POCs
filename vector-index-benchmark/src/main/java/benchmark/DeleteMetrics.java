package benchmark;

public class DeleteMetrics {
    private final int vectorsDeleted;
    private final long totalTimeMs;
    private final double p50Micros;
    private final double p95Micros;
    private final double p99Micros;
    private final double deletesPerSecond;

    public DeleteMetrics(int vectorsDeleted, long totalTimeMs, double p50Micros, double p95Micros, double p99Micros, double deletesPerSecond) {
        this.vectorsDeleted = vectorsDeleted;
        this.totalTimeMs = totalTimeMs;
        this.p50Micros = p50Micros;
        this.p95Micros = p95Micros;
        this.p99Micros = p99Micros;
        this.deletesPerSecond = deletesPerSecond;
    }

    public int getVectorsDeleted() {
        return vectorsDeleted;
    }

    public long getTotalTimeMs() {
        return totalTimeMs;
    }

    public double getP50Micros() {
        return p50Micros;
    }

    public double getP95Micros() {
        return p95Micros;
    }

    public double getP99Micros() {
        return p99Micros;
    }

    public double getDeletesPerSecond() {
        return deletesPerSecond;
    }

    @Override
    public String toString() {
        return String.format(
                """
                        Deleted: %d vectors
                        Total Time: %d ms
                        Latency P50: %.2f μs
                        Latency P95: %.2f μs
                        Latency P99: %.2f μs
                        Throughput: %.2f deletes/sec""",
                vectorsDeleted, totalTimeMs, p50Micros, p95Micros, p99Micros, deletesPerSecond
        );
    }
}

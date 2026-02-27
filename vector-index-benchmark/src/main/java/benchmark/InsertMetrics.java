package benchmark;

public class InsertMetrics {
    private final int vectorsInserted;
    private final long totalTimeMs;
    private final double p50Micros;
    private final double p95Micros;
    private final double p99Micros;
    private final double insertsPerSecond;

    public InsertMetrics(int vectorsInserted, long totalTimeMs, double p50Micros, double p95Micros, double p99Micros, double insertsPerSecond) {
        this.vectorsInserted = vectorsInserted;
        this.totalTimeMs = totalTimeMs;
        this.p50Micros = p50Micros;
        this.p95Micros = p95Micros;
        this.p99Micros = p99Micros;
        this.insertsPerSecond = insertsPerSecond;
    }

    public int getVectorsInserted() {
        return vectorsInserted;
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

    public double getInsertsPerSecond() {
        return insertsPerSecond;
    }

    @Override
    public String toString() {
        return String.format(
                """
                        Inserted: %d vectors
                        Total Time: %d ms
                        Latency P50: %.2f μs
                        Latency P95: %.2f μs
                        Latency P99: %.2f μs
                        Throughput: %.2f inserts/sec""",
                vectorsInserted, totalTimeMs, p50Micros, p95Micros, p99Micros, insertsPerSecond
        );
    }
}

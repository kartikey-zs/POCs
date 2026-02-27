package benchmark;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

// Manages executor service instances for benchmarking
public class BenchmarkExecutors {
    private static ExecutorService batchInsertExecutor;

    public static synchronized ExecutorService getBatchInsertExecutor() {
        if (batchInsertExecutor==null || batchInsertExecutor.isShutdown()) {
            int threads = Runtime.getRuntime().availableProcessors();
            System.out.println("Creating batch insert executor with " + threads + " threads...");

            batchInsertExecutor = Executors.newFixedThreadPool(threads, new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setName("batch-insert" + counter);
                    return t;
                }
            });
        }
        return batchInsertExecutor;
    }

    // create a custom executor with specified thread count
    // useful for testing different parallelism levels
    public static ExecutorService createCustomExecutor(int threads) {
        System.out.println("Creating custom executor with " + threads + " threads");
        return Executors.newFixedThreadPool(threads, new ThreadFactory() {
            private int counter = 0;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("custom-insert-" + counter);
                return t;
            }
        });
    }

    // shutdown all managed executors
    // call this at the end of the benchmark
    public static synchronized void shutdown() {
        if (batchInsertExecutor!=null && !batchInsertExecutor.isShutdown()) {
            System.out.println("Shutting down batch insert executor");
            batchInsertExecutor.shutdown();
            try {
                if (!batchInsertExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.out.println("Forcing shutdown");
                    batchInsertExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                batchInsertExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // shutdown a specific executor
    public static synchronized void shutdown(ExecutorService executorService) {
        if (executorService!=null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.out.println("Forcing shutdown");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}

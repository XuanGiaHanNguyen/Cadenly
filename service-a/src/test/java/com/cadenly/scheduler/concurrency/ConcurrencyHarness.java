package com.cadenly.scheduler.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

/**
 * Runs N tasks on N threads, holding every thread at a CountDownLatch gate
 * until all are submitted, then releasing them together so the maximum
 * number of threads actually contend at once - forcing real interleaving
 * instead of a trickle of near-sequential calls.
 */
public final class ConcurrencyHarness {

    private ConcurrencyHarness() {
    }

    public record Result(List<Boolean> outcomes, long elapsedNanos) {
        public long successCount() {
            return outcomes.stream().filter(Boolean::booleanValue).count();
        }

        public double throughputPerSecond() {
            return outcomes.size() / (elapsedNanos / 1_000_000_000.0);
        }
    }

    public static Result runConcurrently(int threadCount, IntFunction<Callable<Boolean>> taskFactory) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>(threadCount);

        for (int i = 0; i < threadCount; i++) {
            Callable<Boolean> task = taskFactory.apply(i);
            futures.add(pool.submit(() -> {
                startGate.await();
                return task.call();
            }));
        }

        long startNanos = System.nanoTime();
        startGate.countDown();

        List<Boolean> outcomes = new ArrayList<>(threadCount);
        try {
            for (Future<Boolean> future : futures) {
                outcomes.add(future.get());
            }
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            pool.shutdown();
        }
        long elapsedNanos = System.nanoTime() - startNanos;

        return new Result(outcomes, elapsedNanos);
    }

    /**
     * Same gated-release orchestration as runConcurrently, generalized to
     * any return type and recording each task's individual latency (timed
     * from just before task.call() to just after, inside the worker thread
     * - not counting time spent waiting at the gate). Exceptions are
     * captured per-outcome rather than propagated, so one failing request
     * doesn't abort the whole batch - needed for load tests where some
     * requests are expected to fail/reject under contention.
     */
    public record TimedOutcome<T>(T value, long latencyNanos, Throwable error) {
        public boolean succeeded() {
            return error == null;
        }
    }

    public record TimedResult<T>(List<TimedOutcome<T>> outcomes, long elapsedNanos) {
        public double throughputPerSecond() {
            return outcomes.size() / (elapsedNanos / 1_000_000_000.0);
        }

        /** Nearest-rank percentile, e.g. percentileLatencyMillis(95) for p95. */
        public long percentileLatencyMillis(double percentile) {
            List<Long> sorted = outcomes.stream()
                    .map(TimedOutcome::latencyNanos)
                    .sorted()
                    .toList();
            int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
            index = Math.max(0, Math.min(index, sorted.size() - 1));
            return sorted.get(index) / 1_000_000;
        }
    }

    public static <T> TimedResult<T> runConcurrentlyTimed(int threadCount, IntFunction<Callable<T>> taskFactory) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<TimedOutcome<T>>> futures = new ArrayList<>(threadCount);

        for (int i = 0; i < threadCount; i++) {
            Callable<T> task = taskFactory.apply(i);
            futures.add(pool.submit(() -> {
                startGate.await();
                long taskStart = System.nanoTime();
                try {
                    T value = task.call();
                    return new TimedOutcome<>(value, System.nanoTime() - taskStart, null);
                } catch (Exception e) {
                    return new TimedOutcome<>((T) null, System.nanoTime() - taskStart, e);
                }
            }));
        }

        long startNanos = System.nanoTime();
        startGate.countDown();

        List<TimedOutcome<T>> outcomes = new ArrayList<>(threadCount);
        try {
            for (Future<TimedOutcome<T>> future : futures) {
                outcomes.add(future.get());
            }
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            pool.shutdown();
        }
        long elapsedNanos = System.nanoTime() - startNanos;

        return new TimedResult<>(outcomes, elapsedNanos);
    }
}

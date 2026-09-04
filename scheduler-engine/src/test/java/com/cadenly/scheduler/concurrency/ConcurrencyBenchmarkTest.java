package com.cadenly.scheduler.concurrency;

import com.cadenly.scheduler.model.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Benchmarks pessimistic (ReentrantLock per resource) against optimistic
 * (version-stamp + CAS retry) under two regimes against the SAME shared
 * resource:
 *
 * Scenario A - true contention: every thread wants the exact same slot,
 * so only one can ever legitimately win regardless of strategy.
 *
 * Scenario B - false contention: every thread wants a distinct,
 * non-overlapping slot, so all of them are logically compatible - but
 * both strategies still serialize access to that one resource's state,
 * because granularity here is per-resource, not per-slot.
 *
 * Prints throughput and (for optimistic) wasted CAS retries, so the
 * pessimistic-vs-optimistic tradeoff has real numbers behind it instead of
 * just the theoretical argument.
 *
 * As of the Phase 10 Postgres migration, this benchmarks a case-study
 * fixture (see SharedResourceCalendar's class doc), not the production
 * booking path - kept because the numbers are still real and the tradeoff
 * they demonstrate is still true, just no longer the mechanism preventing
 * double-booking in production.
 */
class ConcurrencyBenchmarkTest {

    private final Instant now = Instant.parse("2026-09-03T09:00:00Z");
    private final int threads = 200;

    @Test
    void benchmarkPessimisticVsOptimistic_underHighAndFalseContention() throws InterruptedException {
        System.out.println("=== Concurrency Benchmark: Pessimistic (ReentrantLock) vs Optimistic (CAS+retry) ===");
        System.out.printf("threads=%d%n%n", threads);

        runScenarioA();
        System.out.println();
        runScenarioB();
    }

    private void runScenarioA() throws InterruptedException {
        TimeSlot sharedSlot = new TimeSlot(now, now.plus(Duration.ofHours(1)));

        SharedResourceCalendar pessimistic = new SharedResourceCalendar();
        UUID pessimisticResource = UUID.randomUUID();
        ConcurrencyHarness.Result pResult = ConcurrencyHarness.runConcurrently(
                threads, i -> () -> pessimistic.tryBook(pessimisticResource, sharedSlot));

        OptimisticResourceCalendar optimistic = new OptimisticResourceCalendar();
        UUID optimisticResource = UUID.randomUUID();
        ConcurrencyHarness.Result oResult = ConcurrencyHarness.runConcurrently(
                threads, i -> () -> optimistic.tryBook(optimisticResource, sharedSlot));

        System.out.println("Scenario A - SAME overlapping slot, ONE resource (exclusive: only 1 can legitimately win)");
        report("Pessimistic", pResult, -1);
        report("Optimistic", oResult, optimistic.totalRetries());

        assertThat(pResult.successCount()).isEqualTo(1);
        assertThat(oResult.successCount()).isEqualTo(1);
    }

    private void runScenarioB() throws InterruptedException {
        SharedResourceCalendar pessimistic = new SharedResourceCalendar();
        UUID pessimisticResource = UUID.randomUUID();
        ConcurrencyHarness.Result pResult = ConcurrencyHarness.runConcurrently(
                threads, i -> () -> pessimistic.tryBook(pessimisticResource,
                        new TimeSlot(now.plus(Duration.ofHours(i)), now.plus(Duration.ofHours(i + 1)))));

        OptimisticResourceCalendar optimistic = new OptimisticResourceCalendar();
        UUID optimisticResource = UUID.randomUUID();
        ConcurrencyHarness.Result oResult = ConcurrencyHarness.runConcurrently(
                threads, i -> () -> optimistic.tryBook(optimisticResource,
                        new TimeSlot(now.plus(Duration.ofHours(i)), now.plus(Duration.ofHours(i + 1)))));

        System.out.println("Scenario B - DISTINCT non-overlapping slots, ONE resource (all should succeed)");
        report("Pessimistic", pResult, -1);
        report("Optimistic", oResult, optimistic.totalRetries());

        assertThat(pResult.successCount()).isEqualTo(threads);
        assertThat(oResult.successCount()).isEqualTo(threads);
    }

    private void report(String label, ConcurrencyHarness.Result result, long retries) {
        String retryPart = retries >= 0 ? String.format(", retries=%d", retries) : "";
        System.out.printf("  %-11s successes=%d/%d  elapsed=%.1fms  throughput=%.0f ops/sec%s%n",
                label, result.successCount(), result.outcomes().size(),
                result.elapsedNanos() / 1_000_000.0, result.throughputPerSecond(), retryPart);
    }
}

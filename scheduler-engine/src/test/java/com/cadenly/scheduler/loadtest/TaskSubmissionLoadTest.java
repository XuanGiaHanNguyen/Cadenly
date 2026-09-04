package com.cadenly.scheduler.loadtest;

import com.cadenly.scheduler.concurrency.ConcurrencyHarness;
import com.cadenly.scheduler.model.TaskSubmissionItem;
import com.cadenly.scheduler.model.TaskSubmissionRequest;
import com.cadenly.scheduler.model.TaskSubmissionResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real HTTP load test against a real embedded server (RANDOM_PORT), not a
 * shortcut calling SchedulingService directly - exercises the actual
 * REST/Jackson/Spring MVC stack under concurrent load.
 *
 * Tagged "load" and excluded from the default `mvn test` run (see pom.xml)
 * since it intentionally stresses the system with 200+ concurrent requests
 * and takes longer than a unit test. Run explicitly with:
 *   mvn test -Dgroups=load -DexcludedGroups=
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("load")
class TaskSubmissionLoadTest {

    private static final int THREADS = 200;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void manyDistinctOwners_measuresThroughputCeiling() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofDays(7)); // generous window - no real conflicts expected

        ConcurrencyHarness.TimedResult<TaskSubmissionResponse> result = ConcurrencyHarness.runConcurrentlyTimed(
                THREADS,
                i -> () -> {
                    TaskSubmissionRequest request = new TaskSubmissionRequest(List.of(
                            new TaskSubmissionItem("loadtest-user-" + i, "Load test task " + i, deadline, 5, 30)
                    ));
                    return restTemplate.postForObject(url("/api/tasks/submit"), request, TaskSubmissionResponse.class);
                }
        );

        report("Scenario A - many distinct owners (throughput ceiling)", result);

        long errors = result.outcomes().stream().filter(o -> !o.succeeded()).count();
        assertThat(errors).isZero();
    }

    @Test
    void singleContendedOwner_measuresRealisticContention() throws InterruptedException {
        // 2-hour window, 30-minute tasks -> only ~4 non-overlapping slots actually
        // exist, so most of these 200 concurrent requests for the SAME owner must
        // either lose a concurrency race or find no free slot left at all.
        Instant deadline = Instant.now().plus(Duration.ofHours(2));

        ConcurrencyHarness.TimedResult<TaskSubmissionResponse> result = ConcurrencyHarness.runConcurrentlyTimed(
                THREADS,
                i -> () -> {
                    TaskSubmissionRequest request = new TaskSubmissionRequest(List.of(
                            new TaskSubmissionItem("loadtest-user-999", "Contended task " + i, deadline, 5, 30)
                    ));
                    return restTemplate.postForObject(url("/api/tasks/submit"), request, TaskSubmissionResponse.class);
                }
        );

        report("Scenario B - single contended owner (realistic contention)", result);

        long errors = result.outcomes().stream().filter(o -> !o.succeeded()).count();
        assertThat(errors).isZero(); // no transport/server errors - rejections are a normal, correctly-reported outcome
    }

    private void report(String label, ConcurrencyHarness.TimedResult<TaskSubmissionResponse> result) {
        long placed = 0, rejected = 0, unresolved = 0, errors = 0;
        for (var outcome : result.outcomes()) {
            if (!outcome.succeeded()) {
                errors++;
                continue;
            }
            TaskSubmissionResponse r = outcome.value();
            placed += r.placed().size();
            rejected += r.rejected().size();
            unresolved += r.unresolved().size();
        }

        System.out.println(label);
        System.out.printf("  requests=%d  throughput=%.0f req/sec  errors=%d%n",
                result.outcomes().size(), result.throughputPerSecond(), errors);
        System.out.printf("  p50=%dms  p95=%dms  p99=%dms%n",
                result.percentileLatencyMillis(50), result.percentileLatencyMillis(95), result.percentileLatencyMillis(99));
        System.out.printf("  placed=%d  rejected=%d  unresolved=%d%n%n", placed, rejected, unresolved);
    }
}

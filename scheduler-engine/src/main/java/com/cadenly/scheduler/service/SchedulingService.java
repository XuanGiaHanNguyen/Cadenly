package com.cadenly.scheduler.service;

import com.cadenly.scheduler.model.Candidate;
import com.cadenly.scheduler.model.OwnerResolution;
import com.cadenly.scheduler.model.PlacedTaskResponse;
import com.cadenly.scheduler.model.RejectedTaskResponse;
import com.cadenly.scheduler.model.ScheduleResult;
import com.cadenly.scheduler.model.ScheduledTask;
import com.cadenly.scheduler.model.Task;
import com.cadenly.scheduler.model.TaskSubmissionItem;
import com.cadenly.scheduler.model.TaskSubmissionResponse;
import com.cadenly.scheduler.model.TimeSlot;
import com.cadenly.scheduler.model.UnresolvedTaskResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The recording-pipeline-facing entry point this project's Phase 1-2 design
 * called out as a seam: "the recording pipeline should call a single
 * scheduler-engine method, e.g. SchedulingService.submitTasks(List<Task>)".
 * TaskSubmissionController is the thin REST adapter over this.
 *
 * Owner resolution happens here (not in the recording pipeline) because the scheduler engine owns
 * the User/Calendar domain; a task whose owner can't be resolved never
 * becomes a Task domain object or reaches the scheduler at all - there's
 * no meaningful calendar to schedule it against.
 */
@Service
public class SchedulingService {

    private final OwnerResolver ownerResolver;
    private final FreeSlotService freeSlotService;
    private final CandidateGenerator candidateGenerator;
    private final WeightedIntervalScheduler weightedIntervalScheduler;
    private final WeightCalculator weightCalculator;
    private final SharedResourceCalendar sharedResourceCalendar;

    public SchedulingService(
            OwnerResolver ownerResolver,
            FreeSlotService freeSlotService,
            CandidateGenerator candidateGenerator,
            WeightedIntervalScheduler weightedIntervalScheduler,
            WeightCalculator weightCalculator,
            SharedResourceCalendar sharedResourceCalendar
    ) {
        this.ownerResolver = ownerResolver;
        this.freeSlotService = freeSlotService;
        this.candidateGenerator = candidateGenerator;
        this.weightedIntervalScheduler = weightedIntervalScheduler;
        this.weightCalculator = weightCalculator;
        this.sharedResourceCalendar = sharedResourceCalendar;
    }

    public TaskSubmissionResponse submitTasks(List<TaskSubmissionItem> items) {
        Instant now = Instant.now();

        List<UnresolvedTaskResponse> unresolved = new ArrayList<>();
        List<RejectedTaskResponse> rejected = new ArrayList<>();
        List<PlacedTaskResponse> placed = new ArrayList<>();

        Map<UUID, List<Task>> tasksByOwner = new LinkedHashMap<>();
        for (TaskSubmissionItem item : items) {
            OwnerResolution resolution = ownerResolver.resolve(item.ownerName());
            if (!resolution.isResolved()) {
                unresolved.add(new UnresolvedTaskResponse(item.ownerName(), item.description(), resolution.unresolvedReason()));
                continue;
            }
            Task task = new Task(
                    UUID.randomUUID(),
                    resolution.ownerId(),
                    item.description(),
                    item.deadline(),
                    item.priority(),
                    Duration.ofMinutes(item.estimatedDurationMinutes())
            );
            tasksByOwner.computeIfAbsent(resolution.ownerId(), id -> new ArrayList<>()).add(task);
        }

        for (Map.Entry<UUID, List<Task>> entry : tasksByOwner.entrySet()) {
            UUID ownerId = entry.getKey();
            List<Task> tasks = entry.getValue();
            scheduleForOwner(ownerId, tasks, now, placed, rejected);
        }

        return new TaskSubmissionResponse(placed, rejected, unresolved);
    }

    private void scheduleForOwner(
            UUID ownerId,
            List<Task> tasks,
            Instant now,
            List<PlacedTaskResponse> placed,
            List<RejectedTaskResponse> rejected
    ) {
        Instant horizonEnd = tasks.stream()
                .map(Task::deadline)
                .max(Instant::compareTo)
                .orElse(now.plus(Duration.ofDays(14)));

        List<TimeSlot> existingBusy = sharedResourceCalendar.bookingsFor(ownerId);
        List<TimeSlot> freeSlots = freeSlotService.freeSlotsFromBusy(existingBusy, now, horizonEnd);

        Map<UUID, Task> tasksById = new LinkedHashMap<>();
        List<Candidate> candidates = new ArrayList<>();
        for (Task task : tasks) {
            tasksById.put(task.id(), task);
            Optional<Candidate> candidate = candidateGenerator.candidateFor(task, freeSlots, weightCalculator, now);
            if (candidate.isPresent()) {
                candidates.add(candidate.get());
            } else {
                rejected.add(new RejectedTaskResponse(task.description(), ownerId, "no free slot available before deadline"));
            }
        }

        ScheduleResult result = weightedIntervalScheduler.schedule(candidates);

        for (ScheduledTask scheduled : result.placed()) {
            boolean committed = sharedResourceCalendar.tryBook(ownerId, new TimeSlot(scheduled.start(), scheduled.end()));
            String description = tasksById.get(scheduled.taskId()).description();
            if (committed) {
                placed.add(new PlacedTaskResponse(description, ownerId, scheduled.start(), scheduled.end()));
            } else {
                // The DP selected this slot from a snapshot of bookingsFor(ownerId); tryBook is
                // the final, concurrency-safe authority and can still lose a genuine race that
                // landed between the snapshot and the commit.
                rejected.add(new RejectedTaskResponse(description, ownerId, "lost concurrency race for this slot"));
            }
        }
        for (Task task : result.rejected()) {
            rejected.add(new RejectedTaskResponse(task.description(), ownerId, "not selected by scheduler (lower priority/weight)"));
        }
    }
}

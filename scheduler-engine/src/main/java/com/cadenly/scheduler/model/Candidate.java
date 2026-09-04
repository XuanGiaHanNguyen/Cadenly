package com.cadenly.scheduler.model;

/**
 * A single task bound to its one earliest-fit candidate interval, with a
 * resolved scheduling weight. This is the unit the weighted interval
 * scheduling DP operates on.
 */
public record Candidate(Task task, TimeSlot interval, long weight) {
}

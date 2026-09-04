package com.cadenly.scheduler.service;

import com.cadenly.scheduler.model.CalendarEvent;
import com.cadenly.scheduler.model.TimeSlot;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sweep-line free-slot detection: merges a user's busy intervals, then takes
 * the complement within [rangeStart, rangeEnd). Also supports intersecting
 * free-slot lists across multiple users (e.g. multi-attendee meetings, or a
 * shared resource like a room).
 */
@Service
public class FreeSlotService {

    public List<TimeSlot> freeSlots(List<CalendarEvent> events, Instant rangeStart, Instant rangeEnd) {
        List<TimeSlot> busySlots = events.stream()
                .filter(e -> e.start().isBefore(e.end())) // skip zero/negative-length events defensively
                .map(e -> new TimeSlot(e.start(), e.end()))
                .toList();
        return freeSlotsFromBusy(busySlots, rangeStart, rangeEnd);
    }

    /**
     * Same free-slot computation, but taking busy TimeSlots directly rather
     * than CalendarEvents - lets SharedResourceCalendar's live bookings feed
     * straight in as the busy-time source, without fabricating fake
     * CalendarEvent wrappers around them.
     */
    public List<TimeSlot> freeSlotsFromBusy(List<TimeSlot> busySlots, Instant rangeStart, Instant rangeEnd) {
        if (!rangeStart.isBefore(rangeEnd)) {
            return List.of();
        }
        List<TimeSlot> merged = mergeIntervals(busySlots);

        List<TimeSlot> gaps = new ArrayList<>();
        Instant cursor = rangeStart;
        for (TimeSlot busy : merged) {
            Instant busyStart = busy.start().isBefore(rangeStart) ? rangeStart : busy.start();
            Instant busyEnd = busy.end().isAfter(rangeEnd) ? rangeEnd : busy.end();
            if (busyStart.isAfter(rangeEnd)) {
                break;
            }
            if (busyStart.isAfter(cursor)) {
                gaps.add(new TimeSlot(cursor, busyStart));
            }
            if (busyEnd.isAfter(cursor)) {
                cursor = busyEnd;
            }
        }
        if (cursor.isBefore(rangeEnd)) {
            gaps.add(new TimeSlot(cursor, rangeEnd));
        }
        return gaps;
    }

    public List<TimeSlot> intersectFreeSlots(List<List<TimeSlot>> slotLists) {
        if (slotLists.isEmpty()) {
            return List.of();
        }
        List<TimeSlot> result = slotLists.get(0);
        for (int i = 1; i < slotLists.size(); i++) {
            result = pairwiseIntersect(result, slotLists.get(i));
        }
        return result;
    }

    private List<TimeSlot> mergeIntervals(List<TimeSlot> slots) {
        List<TimeSlot> sorted = slots.stream()
                .sorted(Comparator.comparing(TimeSlot::start))
                .toList();

        List<TimeSlot> merged = new ArrayList<>();
        for (TimeSlot s : sorted) {
            if (merged.isEmpty() || s.start().isAfter(merged.get(merged.size() - 1).end())) {
                merged.add(s);
            } else {
                TimeSlot last = merged.remove(merged.size() - 1);
                Instant newEnd = s.end().isAfter(last.end()) ? s.end() : last.end();
                merged.add(new TimeSlot(last.start(), newEnd));
            }
        }
        return merged;
    }

    private List<TimeSlot> pairwiseIntersect(List<TimeSlot> a, List<TimeSlot> b) {
        List<TimeSlot> out = new ArrayList<>();
        int i = 0, j = 0;
        while (i < a.size() && j < b.size()) {
            TimeSlot sa = a.get(i);
            TimeSlot sb = b.get(j);
            Instant start = sa.start().isAfter(sb.start()) ? sa.start() : sb.start();
            Instant end = sa.end().isBefore(sb.end()) ? sa.end() : sb.end();
            if (start.isBefore(end)) {
                out.add(new TimeSlot(start, end));
            }
            if (sa.end().isBefore(sb.end())) {
                i++;
            } else {
                j++;
            }
        }
        return out;
    }
}

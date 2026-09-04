package com.cadenly.scheduler.service;

import com.cadenly.scheduler.model.CalendarEvent;
import com.cadenly.scheduler.model.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FreeSlotServiceTest {

    private final FreeSlotService service = new FreeSlotService();
    private final Instant day = Instant.parse("2026-09-03T09:00:00Z");
    private final UUID user = UUID.randomUUID();

    private CalendarEvent event(Instant start, Instant end) {
        return new CalendarEvent(UUID.randomUUID(), user, start, end);
    }

    @Test
    void noEvents_wholeRangeIsFree() {
        List<TimeSlot> gaps = service.freeSlots(List.of(), day, day.plus(Duration.ofHours(8)));
        assertThat(gaps).containsExactly(new TimeSlot(day, day.plus(Duration.ofHours(8))));
    }

    @Test
    void singleEvent_producesGapBeforeAndAfter() {
        Instant rangeEnd = day.plus(Duration.ofHours(8));
        CalendarEvent meeting = event(day.plus(Duration.ofHours(2)), day.plus(Duration.ofHours(3)));

        List<TimeSlot> gaps = service.freeSlots(List.of(meeting), day, rangeEnd);

        assertThat(gaps).containsExactly(
                new TimeSlot(day, day.plus(Duration.ofHours(2))),
                new TimeSlot(day.plus(Duration.ofHours(3)), rangeEnd)
        );
    }

    @Test
    void overlappingEvents_areMergedIntoOneBusyBlock() {
        Instant rangeEnd = day.plus(Duration.ofHours(8));
        CalendarEvent a = event(day.plus(Duration.ofHours(1)), day.plus(Duration.ofHours(3)));
        CalendarEvent b = event(day.plus(Duration.ofHours(2)), day.plus(Duration.ofHours(4)));

        List<TimeSlot> gaps = service.freeSlots(List.of(a, b), day, rangeEnd);

        assertThat(gaps).containsExactly(
                new TimeSlot(day, day.plus(Duration.ofHours(1))),
                new TimeSlot(day.plus(Duration.ofHours(4)), rangeEnd)
        );
    }

    @Test
    void backToBackEvents_touchingExactly_mergeWithoutZeroLengthGap() {
        Instant rangeEnd = day.plus(Duration.ofHours(8));
        CalendarEvent a = event(day.plus(Duration.ofHours(1)), day.plus(Duration.ofHours(2)));
        CalendarEvent b = event(day.plus(Duration.ofHours(2)), day.plus(Duration.ofHours(3))); // b.start == a.end

        List<TimeSlot> gaps = service.freeSlots(List.of(a, b), day, rangeEnd);

        assertThat(gaps).containsExactly(
                new TimeSlot(day, day.plus(Duration.ofHours(1))),
                new TimeSlot(day.plus(Duration.ofHours(3)), rangeEnd)
        );
    }

    @Test
    void freeSlotsFromBusy_producesTheSameResultAsFreeSlots_givenEquivalentInput() {
        Instant rangeEnd = day.plus(Duration.ofHours(8));
        TimeSlot busy = new TimeSlot(day.plus(Duration.ofHours(2)), day.plus(Duration.ofHours(3)));

        List<TimeSlot> gaps = service.freeSlotsFromBusy(List.of(busy), day, rangeEnd);

        assertThat(gaps).containsExactly(
                new TimeSlot(day, day.plus(Duration.ofHours(2))),
                new TimeSlot(day.plus(Duration.ofHours(3)), rangeEnd)
        );
    }

    @Test
    void freeSlotsFromBusy_mergesOverlappingBusySlots() {
        Instant rangeEnd = day.plus(Duration.ofHours(8));
        TimeSlot a = new TimeSlot(day.plus(Duration.ofHours(1)), day.plus(Duration.ofHours(3)));
        TimeSlot b = new TimeSlot(day.plus(Duration.ofHours(2)), day.plus(Duration.ofHours(4)));

        List<TimeSlot> gaps = service.freeSlotsFromBusy(List.of(a, b), day, rangeEnd);

        assertThat(gaps).containsExactly(
                new TimeSlot(day, day.plus(Duration.ofHours(1))),
                new TimeSlot(day.plus(Duration.ofHours(4)), rangeEnd)
        );
    }

    @Test
    void intersectFreeSlots_acrossTwoUsers_returnsOnlyCommonWindows() {
        Instant rangeEnd = day.plus(Duration.ofHours(8));
        // user A free: [0,2) [4,8);  user B free: [1,3) [5,8)
        List<TimeSlot> userAFree = List.of(
                new TimeSlot(day, day.plus(Duration.ofHours(2))),
                new TimeSlot(day.plus(Duration.ofHours(4)), rangeEnd)
        );
        List<TimeSlot> userBFree = List.of(
                new TimeSlot(day.plus(Duration.ofHours(1)), day.plus(Duration.ofHours(3))),
                new TimeSlot(day.plus(Duration.ofHours(5)), rangeEnd)
        );

        List<TimeSlot> common = service.intersectFreeSlots(List.of(userAFree, userBFree));

        assertThat(common).containsExactly(
                new TimeSlot(day.plus(Duration.ofHours(1)), day.plus(Duration.ofHours(2))),
                new TimeSlot(day.plus(Duration.ofHours(5)), rangeEnd)
        );
    }
}

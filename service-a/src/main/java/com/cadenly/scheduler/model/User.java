package com.cadenly.scheduler.model;

import java.util.List;
import java.util.UUID;

public record User(UUID id, String name, List<CalendarEvent> events) {
}

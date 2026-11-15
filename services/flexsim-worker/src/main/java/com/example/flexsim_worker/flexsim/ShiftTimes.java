package com.example.flexsim_worker.flexsim;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Reads the start and end of the shift from the job, like "2026-10-21T23:00:00".
 *
 * NIGHT SHIFT rule: if the end is BEFORE the start, the shift ends on the NEXT day.
 *   start 2026-10-21T23:00, end 2026-10-21T06:30  ->  end becomes 2026-10-22T06:30
 * End EQUAL to start = a shift of 0 minutes = not valid.
 */
public final class ShiftTimes {

    private ShiftTimes() {
    }

    public record Shift(LocalDateTime start, LocalDateTime end) {
    }

    public static Shift parse(String startText, String endText) {
        LocalDateTime start = read(startText, "startTime");
        LocalDateTime end = read(endText, "endTime");
        if (end.isEqual(start)) {
            throw new IllegalArgumentException("startTime and endTime are the same (" + startText + ")");
        }
        if (end.isBefore(start)) {
            end = end.plusDays(1);   // night shift
        }
        return new Shift(start, end);
    }

    private static LocalDateTime read(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is missing");
        }
        String value = text.trim();
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            // maybe it has a zone, like 2026-10-21T23:00:00Z
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(fieldName + " is not a valid date-time: " + value);
        }
    }
}

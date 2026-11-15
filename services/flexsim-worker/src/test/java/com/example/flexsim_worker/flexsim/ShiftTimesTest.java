package com.example.flexsim_worker.flexsim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ShiftTimesTest {

    @Test
    void dayShiftStaysOnTheSameDay() {
        ShiftTimes.Shift shift = ShiftTimes.parse("2026-10-21T08:00:00", "2026-10-21T16:00:00");

        assertThat(shift.end()).isEqualTo(LocalDateTime.of(2026, 10, 21, 16, 0));
    }

    @Test
    void nightShiftEndsOnTheNextDay() {
        ShiftTimes.Shift shift = ShiftTimes.parse("2026-10-21T23:00:00", "2026-10-21T06:30:00");

        assertThat(shift.start()).isEqualTo(LocalDateTime.of(2026, 10, 21, 23, 0));
        assertThat(shift.end()).isEqualTo(LocalDateTime.of(2026, 10, 22, 6, 30));
    }

    @Test
    void endAlreadyOnTheNextDayIsKept() {
        ShiftTimes.Shift shift = ShiftTimes.parse("2026-10-21T23:00:00", "2026-10-22T06:30:00");

        assertThat(shift.end()).isEqualTo(LocalDateTime.of(2026, 10, 22, 6, 30));
    }

    @Test
    void timeWithZoneIsAccepted() {
        ShiftTimes.Shift shift = ShiftTimes.parse("2026-10-21T08:00:00Z", "2026-10-21T16:00:00Z");

        assertThat(shift.start()).isEqualTo(LocalDateTime.of(2026, 10, 21, 8, 0));
    }

    @Test
    void sameStartAndEndIsNotValid() {
        assertThatThrownBy(() -> ShiftTimes.parse("2026-10-21T08:00:00", "2026-10-21T08:00:00"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingOrBrokenTimesAreNotValid() {
        assertThatThrownBy(() -> ShiftTimes.parse(null, "2026-10-21T08:00:00"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("startTime");
        assertThatThrownBy(() -> ShiftTimes.parse("2026-10-21T08:00:00", "tomorrow"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("endTime");
    }
}

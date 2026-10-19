package com.example.experiment_service.mapper;

import static com.example.experiment_service.testsupport.StatusAssert.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * This test is in the SAME package as ExperimentMapper on purpose,
 * because parseShiftTime and formatTime are "package-private" (no public keyword).
 */
class ExperimentMapperTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 19);
    private static final LocalDateTime EXPECTED = LocalDateTime.of(2026, 10, 19, 23, 0, 0);

    @Test
    void readsFullIsoForm() {
        assertThat(ExperimentMapper.parseShiftTime("2026-10-19T23:00:00", DAY, "startTime")).isEqualTo(EXPECTED);
    }

    @Test
    void readsSpaceInsteadOfT() {
        assertThat(ExperimentMapper.parseShiftTime("2026-10-19 23:00:00", DAY, "startTime")).isEqualTo(EXPECTED);
    }

    @Test
    void dropsZoneOrOffset() {
        assertThat(ExperimentMapper.parseShiftTime("2026-10-19T23:00:00Z", DAY, "startTime")).isEqualTo(EXPECTED);
        assertThat(ExperimentMapper.parseShiftTime("2026-10-19T23:00:00+05:30", DAY, "startTime")).isEqualTo(EXPECTED);
    }

    @Test
    void timeOnlyTakesTheDateFromSelectedDate() {
        assertThat(ExperimentMapper.parseShiftTime("23:00:00", DAY, "startTime")).isEqualTo(EXPECTED);
        assertThat(ExperimentMapper.parseShiftTime("23:00", DAY, "startTime")).isEqualTo(EXPECTED);
    }

    @Test
    void timeOnlyWithoutSelectedDateUses1970() {
        assertThat(ExperimentMapper.parseShiftTime("06:30:00", null, "endTime"))
                .isEqualTo(LocalDateTime.of(1970, 1, 1, 6, 30, 0));
    }

    @Test
    void nullAndBlankGiveNull() {
        assertThat(ExperimentMapper.parseShiftTime(null, DAY, "startTime")).isNull();
        assertThat(ExperimentMapper.parseShiftTime("   ", DAY, "startTime")).isNull();
    }

    @Test
    void badTextGives400AndNamesTheField() {
        assertStatus(() -> ExperimentMapper.parseShiftTime("not-a-time", DAY, "startTime"), HttpStatus.BAD_REQUEST);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> ExperimentMapper.parseShiftTime("25:99", DAY, "startTime"))
                .hasMessageContaining("startTime");
    }

    @Test
    void formatAlwaysShowsSeconds() {
        assertThat(ExperimentMapper.formatTime(LocalDateTime.of(2026, 10, 19, 23, 0))).isEqualTo("2026-10-19T23:00:00");
        assertThat(ExperimentMapper.formatTime(null)).isNull();
    }
}
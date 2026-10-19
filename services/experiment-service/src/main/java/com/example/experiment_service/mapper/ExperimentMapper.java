package com.example.experiment_service.mapper;

import com.example.experiment_service.dto.ExperimentDto;
import com.example.experiment_service.entity.Experiment;
import com.example.experiment_service.service.ExperimentRules;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns an Experiment (database class) into an ExperimentDto (JSON box) and back.
 * It is written by hand. No MapStruct, so there is no hidden generated code.
 *
 * SECURITY NOTE ("mass assignment"): when we read a DTO, we copy ONLY the fields a user may edit.
 * We never copy experimentId, userId, createdAt or status from the client. Otherwise a client could
 * send "experimentId": 1005 and overwrite somebody else's experiment.
 */
@Component
public class ExperimentMapper {

    public ExperimentDto toDto(Experiment e) {
        ExperimentDto dto = new ExperimentDto();
        dto.setExperimentId(e.getExperimentId());
        dto.setExperimentName(e.getExperimentName());
        dto.setTerminal(e.getTerminal());
        dto.setSortingType(e.getSortingType());
        dto.setUseCaseId(e.getUseCaseId());
        dto.setSelectedDate(e.getSelectedDate());
        dto.setStartTime(formatTime(e.getStartTime()));
        dto.setEndTime(formatTime(e.getEndTime()));
        dto.setParcelCount(e.getParcelCount());
        dto.setNewParcelCount(e.getNewParcelCount());
        dto.setParcelChangeValue(e.getParcelChangeValue());
        dto.setParcelChangeMode(e.getParcelChangeMode());
        dto.setMaxRecirculationCount(e.getMaxRecirculationCount());
        dto.setInitialRunSpeed(e.getInitialRunSpeed());
        dto.setFlexibleResources(e.getFlexibleResources());
        dto.setPhoneBoomChute(e.getPhoneBoomChute());
        dto.setDropChuteFlexible(e.getDropChuteFlexible());
        dto.setFlexibleUnloadResources(e.getFlexibleUnloadResources());
        dto.setSorterFullPercent(e.getSorterFullPercent());
        dto.setMinimumCageOccupancy(e.getMinimumCageOccupancy());
        dto.setMaximumCageOccupancy(e.getMaximumCageOccupancy());
        dto.setGuiMode(e.getGuiMode());
        dto.setAllSortedAt(e.getAllSortedAt());
        dto.setUsername(e.getOwnerUsername());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setStatus(e.getCurrentRunStatus() == null ? null : e.getCurrentRunStatus().name());
        return dto;
    }

    /**
     * Copies the editable fields from the DTO into the entity (used by create AND update),
     * and applies the business rules (limits and defaults) from ExperimentRules.
     */
    public void copyEditableFields(ExperimentDto dto, Experiment e) {
        e.setExperimentName(dto.getExperimentName());
        e.setTerminal(dto.getTerminal());
        e.setSortingType(dto.getSortingType());
        e.setUseCaseId(dto.getUseCaseId() == null ? 0 : dto.getUseCaseId());
        e.setSelectedDate(dto.getSelectedDate());
        e.setStartTime(parseShiftTime(dto.getStartTime(), dto.getSelectedDate(), "startTime"));
        e.setEndTime(parseShiftTime(dto.getEndTime(), dto.getSelectedDate(), "endTime"));
        e.setParcelCount(dto.getParcelCount());
        e.setNewParcelCount(dto.getNewParcelCount());
        e.setParcelChangeValue(dto.getParcelChangeValue());
        e.setParcelChangeMode(dto.getParcelChangeMode());
        e.setMaxRecirculationCount(dto.getMaxRecirculationCount());
        e.setInitialRunSpeed(dto.getInitialRunSpeed());

        Integer flexibleResources = ExperimentRules.flexibleResources(dto.getFlexibleResources());
        e.setFlexibleResources(flexibleResources);
        e.setPhoneBoomChute(ExperimentRules.phoneBoomChute(dto.getPhoneBoomChute(), flexibleResources));
        e.setDropChuteFlexible(ExperimentRules.dropChuteFlexible(dto.getDropChuteFlexible()));
        e.setFlexibleUnloadResources(ExperimentRules.flexibleUnloadResources(dto.getFlexibleUnloadResources()));
        e.setSorterFullPercent(ExperimentRules.sorterFullPercent(dto.getSorterFullPercent()));
        Integer minimum = ExperimentRules.minimumCageOccupancy(dto.getMinimumCageOccupancy());
        e.setMinimumCageOccupancy(minimum);
        e.setMaximumCageOccupancy(ExperimentRules.maximumCageOccupancy(dto.getMaximumCageOccupancy(), minimum));
        e.setGuiMode(dto.getGuiMode() != null ? dto.getGuiMode() : "GUI");
        e.setAllSortedAt(dto.getAllSortedAt());
    }

    /** Always writes the full form: 2026-10-19T23:00:00 (seconds are always shown). */
    static String formatTime(LocalDateTime value) {
        return value == null ? null : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value);
    }

    /**
     * Reads a shift time. Accepted forms:
     *   2026-10-19T23:00:00      (full)
     *   2026-10-19 23:00:00      (space instead of T)
     *   2026-10-19T23:00:00Z     (a zone or offset is dropped: shift times have no time zone)
     *   23:00:00 or 23:00        (time only: the date comes from selectedDate, or 1970-01-01 if missing)
     * Anything else gives 400 (Bad Request).
     */
    static LocalDateTime parseShiftTime(String raw, LocalDate selectedDate, String fieldName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            // try the next form
        }
        try {
            return LocalDateTime.parse(text.replace(' ', 'T'));
        } catch (DateTimeParseException ignored) {
            // try the next form
        }
        try {
            return OffsetDateTime.parse(text).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // try the next form
        }
        try {
            LocalDate date = selectedDate != null ? selectedDate : LocalDate.of(1970, 1, 1);
            return LocalDateTime.of(date, LocalTime.parse(text));
        } catch (DateTimeParseException ignored) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    fieldName + " is not a valid time. Use 23:00:00 or 2026-10-19T23:00:00");
        }
    }
}
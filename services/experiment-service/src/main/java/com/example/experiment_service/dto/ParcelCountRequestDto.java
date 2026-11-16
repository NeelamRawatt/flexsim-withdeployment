package com.example.experiment_service.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Body of POST /api/parcels/getParcelCount and /getSortPlan (same JSON as the old app):
 *   {"startTime":"2024-11-28T23:00:00","endTime":"2024-11-29T06:30:00","parcelDate":"2024-11-28"}
 * parcelDate is accepted but not used (same as the old app: the times already contain the date).
 */
public record ParcelCountRequestDto(
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        LocalDate parcelDate) {
}

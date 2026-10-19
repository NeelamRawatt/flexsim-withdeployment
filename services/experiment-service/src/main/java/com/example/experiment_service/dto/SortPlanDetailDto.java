package com.example.experiment_service.dto;

/** One sort plan line that the client SENDS (save). */
public record SortPlanDetailDto(
        Long experimentId,
        String oldSortName,
        String newSortName) {
}
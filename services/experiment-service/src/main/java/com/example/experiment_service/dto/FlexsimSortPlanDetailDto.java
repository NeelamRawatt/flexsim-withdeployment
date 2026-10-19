package com.example.experiment_service.dto;

/** One sort plan line that we SEND BACK (read). */
public record FlexsimSortPlanDetailDto(
        String oldSortName,
        String newSortName) {
}
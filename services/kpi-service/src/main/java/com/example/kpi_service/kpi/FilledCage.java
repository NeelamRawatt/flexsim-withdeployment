package com.example.kpi_service.kpi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One full cage at a chute (same fields as the old app's FilledCageDTO).
 *   parcelCount, fillVolume       how full it was
 *   cageFullTime                  when it became full (simulation seconds)
 *   fullCageStoredTime            when the operator stored it
 *   emptyCagePickedTime           when the operator picked an empty cage
 *   cageResumedTime               when sorting into the chute started again
 *   resourceName                  the operator
 *   totalReplacementTime          chute downtime = cageResumedTime - cageFullTime
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FilledCage(
        Integer parcelCount,
        Double fillVolume,
        Double cageFullTime,
        Double fullCageStoredTime,
        Double emptyCagePickedTime,
        Double cageResumedTime,
        String resourceName,
        Double totalReplacementTime) {
}

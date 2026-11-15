package com.example.simulation_service.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The fields WE need from experiment-service's answer to GET /api/experiments/{id}.
 * username = the OWNER. The others are the settings the worker needs to run FlexSim.
 * Times are kept as text exactly as experiment-service sends them (example "2026-10-21T23:00:00").
 * ignoreUnknown = true: the other fields of the answer are skipped.
 *
 * W3: + the FlexSim model settings (useCaseId ... maximumCageOccupancy). They travel in the job
 * message, so the worker has a SNAPSHOT of the settings at the moment the user clicked "Run".
 * (If the user edits the experiment while the run waits, the run still uses what was clicked.)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExperimentSummary(
        Long experimentId,
        String username,
        String experimentName,
        String startTime,
        String endTime,
        Integer maxRecirculationCount,
        Integer useCaseId,
        Integer flexibleResources,
        Integer phoneBoomChute,
        Integer dropChuteFlexible,
        Integer flexibleUnloadResources,
        Integer sorterFullPercent,
        Integer minimumCageOccupancy,
        Integer maximumCageOccupancy) {
}

package com.example.simulation_service.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The fields WE need from experiment-service's answer to GET /api/experiments/{id}.
 * username = the OWNER. The others are the settings the worker needs to run FlexSim.
 * Times are kept as text exactly as experiment-service sends them (example "2026-10-21T23:00:00").
 * ignoreUnknown = true: the ~20 other fields of the answer are skipped.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExperimentSummary(
        Long experimentId,
        String username,
        String experimentName,
        String startTime,
        String endTime,
        Integer maxRecirculationCount) {
}
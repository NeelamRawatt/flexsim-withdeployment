package com.example.flexsim_worker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One run, as simulation-service shows it (GET /api/simulation/runs/{runId}).
 * We keep only the fields the worker needs. Other fields in the JSON are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RunView(
        Long runId,
        Long experimentId,
        RunStatus status,
        String stage,
        String message) {
}

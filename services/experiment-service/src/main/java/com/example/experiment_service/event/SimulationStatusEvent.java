package com.example.experiment_service.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The message on topic "simulation.status" (made by simulation-service, same record there).
 *
 * status     = QUEUED, RUNNING, COMPLETED, FAILED or CANCELLED (the RUN's words)
 * stage      = detail inside RUNNING (example: IMPORTING_PARCEL_DATA), may be null
 * message    = short text for people, may be null
 * occurredAt = when it happened, in UTC, like "2026-11-16T07:00:00Z"
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SimulationStatusEvent(
        String eventId,
        Long runId,
        Long experimentId,
        String status,
        String stage,
        String message,
        String occurredAt) {
}

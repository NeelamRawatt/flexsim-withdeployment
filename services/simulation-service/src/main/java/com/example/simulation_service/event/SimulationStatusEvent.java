package com.example.simulation_service.event;

/**
 * The message on topic "simulation.status": the run's status changed.
 * Read by experiment-service (shows the status), kpi-service and ai-query-service (act on COMPLETED).
 * The same topic name as in your team's code.
 *
 * status  = QUEUED, RUNNING, COMPLETED, FAILED or CANCELLED
 * stage   = detail inside RUNNING (example: LOADING_PARCELS), may be null
 * message = a short text for people, may be null
 */
public record SimulationStatusEvent(
        String eventId,
        Long runId,
        Long experimentId,
        String status,
        String stage,
        String message,
        String occurredAt) {
}
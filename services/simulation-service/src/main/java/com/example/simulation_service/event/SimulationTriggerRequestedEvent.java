package com.example.simulation_service.event;

/**
 * The JOB message on topic "simulation.trigger.requested". The flexsim-worker reads it and runs FlexSim.
 * It carries everything the worker needs, so the worker does not have to ask anyone for the settings.
 *
 * eventId   = unique id of THIS message (the worker can skip a message it has already handled)
 * runId     = the row in simulation_run (the worker reports progress for THIS run)
 * experimentId, experimentName, startTime, endTime, maxRecirculationCount = what to simulate
 * requestedBy = username from the token
 * occurredAt  = when, in UTC, like "2026-11-13T07:00:00Z"
 */
public record SimulationTriggerRequestedEvent(
        String eventId,
        Long runId,
        Long experimentId,
        String experimentName,
        String startTime,
        String endTime,
        Integer maxRecirculationCount,
        String requestedBy,
        String occurredAt) {
}
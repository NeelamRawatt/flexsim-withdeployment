package com.example.simulation_service.event;

/**
 * The JOB message on topic "simulation.trigger.requested". The flexsim-worker reads it and runs FlexSim.
 * It carries everything the worker needs, so the worker does not have to ask anyone for the settings.
 *
 * eventId   = unique id of THIS message (the worker can skip a message it has already handled)
 * runId     = the row in simulation_run (the worker reports progress for THIS run)
 * experimentId, experimentName, startTime, endTime, maxRecirculationCount = what to simulate
 * useCaseId ... maximumCageOccupancy = the FlexSim model settings (W3), same names as in experiment-service.
 *                 null = not set: the worker uses the same defaults as the old app.
 * requestedBy = username from the token
 * occurredAt  = when, in UTC, like "2026-11-13T07:00:00Z"
 *
 * The SAME record exists in flexsim-worker. If you add a field here, add it there too.
 */
public record SimulationTriggerRequestedEvent(
        String eventId,
        Long runId,
        Long experimentId,
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
        Integer maximumCageOccupancy,
        String requestedBy,
        String occurredAt) {
}

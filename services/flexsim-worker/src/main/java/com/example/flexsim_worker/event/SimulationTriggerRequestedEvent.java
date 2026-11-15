package com.example.flexsim_worker.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The JOB message on topic "simulation.trigger.requested" (made by simulation-service).
 * The SAME record exists in simulation-service. If a field is added there, add it here too.
 *
 * eventId   = unique id of THIS message
 * runId     = the row in simulation_run (we report progress for THIS run)
 * experimentId, experimentName, startTime, endTime, maxRecirculationCount = what to simulate
 * useCaseId ... maximumCageOccupancy = FlexSim model settings (null = use the default)
 * requestedBy = who clicked "Run"
 * occurredAt  = when, in UTC
 *
 * ignoreUnknown = true: if simulation-service adds a NEW field first, the worker still reads the message.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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

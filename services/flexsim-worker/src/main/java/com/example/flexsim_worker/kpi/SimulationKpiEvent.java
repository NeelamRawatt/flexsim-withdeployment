package com.example.flexsim_worker.kpi;

import tools.jackson.databind.JsonNode;

/**
 * The message on topic "simulation.kpi": ONE KPI snapshot from FlexSim, plus who it belongs to.
 *
 * eventId      unique id of THIS message (kpi-service can skip a message it already saved)
 * runId        the run this snapshot belongs to (FlexSim itself only knows the experimentId)
 * experimentId the experiment
 * receivedAt   when the worker received it, in UTC
 * kpi          the snapshot EXACTLY as FlexSim sent it (chutewiseThroughput, resourceMetrics, ...)
 */
public record SimulationKpiEvent(
        String eventId,
        Long runId,
        Long experimentId,
        String receivedAt,
        JsonNode kpi) {
}

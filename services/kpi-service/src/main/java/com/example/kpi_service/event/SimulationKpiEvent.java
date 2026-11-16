package com.example.kpi_service.event;

import com.example.kpi_service.kpi.SimulationKpi;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The message on topic "simulation.kpi" (made by the flexsim-worker, see its SimulationKpiEvent).
 *   eventId      unique id of this message -> saved only once
 *   runId        the run (FlexSim only knows the experiment; the worker adds the run)
 *   experimentId the experiment
 *   receivedAt   when the worker got it, in UTC
 *   kpi          the snapshot exactly as FlexSim sent it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SimulationKpiEvent(String eventId, Long runId, Long experimentId, String receivedAt, SimulationKpi kpi) {
}

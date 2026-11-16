package com.example.kpi_service.consumer;

import com.example.kpi_service.event.SimulationKpiEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Reads a simulation.kpi message and checks the fields we need. Broken -> InvalidEventException. */
final class KpiEventParser {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private KpiEventParser() {
    }

    static SimulationKpiEvent parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidEventException("Empty simulation.kpi message");
        }
        SimulationKpiEvent event;
        try {
            event = JSON.readValue(value, SimulationKpiEvent.class);
        } catch (JacksonException e) {
            throw new InvalidEventException("simulation.kpi message is not valid JSON", e);
        }
        if (event.eventId() == null || event.eventId().isBlank() || event.experimentId() == null || event.kpi() == null) {
            throw new InvalidEventException("simulation.kpi message has no eventId, experimentId or kpi");
        }
        return event;
    }
}

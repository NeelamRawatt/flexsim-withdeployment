package com.example.simulation_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

/**
 * The body of POST /api/simulation/startSimulation. Only experimentId is needed:
 * the settings are read from experiment-service (the "single source of truth"),
 * so a browser can never send different settings than the saved experiment.
 *
 * ignoreUnknown = true: React today sends the whole experiment; the extra fields are simply ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StartSimulationRequest(@NotNull Long experimentId) {
}
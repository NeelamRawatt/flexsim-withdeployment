package com.example.simulation_service.dto;

import com.example.simulation_service.enums.RunStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/simulation/runs/{runId}/status, sent by the flexsim-worker.
 *
 * Examples:
 *   {"status":"RUNNING","stage":"OPENING_FLEXSIM","message":"Opening FlexSim"}
 *   {"status":"RUNNING","stage":"LOADING_PARCELS","message":"Loading parcels"}
 *   {"status":"COMPLETED","message":"Simulation finished"}
 *   {"status":"FAILED","message":"FlexSim did not start"}
 */
public record RunStatusUpdateRequest(
        @NotNull RunStatus status,
        @Size(max = 50) String stage,
        @Size(max = 500) String message) {
}

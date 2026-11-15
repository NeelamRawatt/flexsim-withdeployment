package com.example.flexsim_worker.api;

/**
 * What FlexSim sends to updateSimulationStage, completeSimulation and modelResetMidRun
 * (same JSON as with the old app):
 *   {"experimentId": 42, "experimentRunStage": "SIMULATION_STARTED", "message": "Simulation Started"}
 */
public record FlexsimStageUpdate(Long experimentId, String experimentRunStage, String message) {
}

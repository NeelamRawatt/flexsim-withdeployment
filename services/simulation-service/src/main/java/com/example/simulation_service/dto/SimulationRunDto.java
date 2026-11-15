package com.example.simulation_service.dto;

import com.example.simulation_service.entity.SimulationRun;
import com.example.simulation_service.enums.RunStatus;
import java.time.LocalDateTime;

/** One run, as React sees it. (runSettings and the version counter are internal.) */
public record SimulationRunDto(
        Long runId,
        Long experimentId,
        RunStatus status,
        String stage,
        String message,
        String requestedBy,
        LocalDateTime queuedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt) {

    public static SimulationRunDto from(SimulationRun run) {
        return new SimulationRunDto(run.getId(), run.getExperimentId(), run.getStatus(), run.getStage(),
                run.getMessage(), run.getRequestedBy(), run.getQueuedAt(), run.getStartedAt(), run.getFinishedAt());
    }
}
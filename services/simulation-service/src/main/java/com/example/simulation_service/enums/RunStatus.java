package com.example.simulation_service.enums;

/**
 * The life of one simulation run:
 *
 *   QUEUED  -> the job is waiting in Kafka for a free FlexSim slot
 *   RUNNING -> the flexsim-worker picked it up and FlexSim is working
 *   COMPLETED / FAILED / CANCELLED -> finished (these 3 are "final": the run never changes again)
 */
public enum RunStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;
 
    /** true for COMPLETED, FAILED and CANCELLED. */
    public boolean isFinal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
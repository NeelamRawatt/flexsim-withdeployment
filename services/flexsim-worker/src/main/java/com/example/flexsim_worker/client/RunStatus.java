package com.example.flexsim_worker.client;

/**
 * The states of a run. MUST be the same names as RunStatus in simulation-service.
 *
 *   QUEUED -> RUNNING -> COMPLETED / FAILED
 *   QUEUED -> CANCELLED   (a user cancelled it before the worker started it)
 */
public enum RunStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    /** true = the run is finished. Nothing will change it any more. */
    public boolean isFinal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}

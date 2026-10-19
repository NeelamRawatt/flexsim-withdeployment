package com.example.experiment_service.enums;

/**
 * The state of the LATEST run of an experiment.
 *
 * An "enum" is a fixed list of allowed words. Java will not let us use any
 * other word, and the database CHECK rule (in the V1 file) is the second guard.
 *
 * The words are stored in the database as TEXT (@Enumerated(EnumType.STRING)).
 * Never store enums as numbers: if somebody puts a new word in the middle of the
 * list, every old number would mean something else.
 */
public enum ExperimentRunStatus {
    PENDING,      // created, never started
    RUNNING,      // simulation is running now
    TERMINATED,   // a user stopped it
    COMPLETED,    // finished well
    FAILED;       // finished with an error

    /** True when the run is finished and will not change any more. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == TERMINATED;
    }
}
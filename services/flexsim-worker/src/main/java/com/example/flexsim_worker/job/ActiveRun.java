package com.example.flexsim_worker.job;

import java.util.concurrent.CompletableFuture;

/**
 * ONE run that is going on right now on this worker.
 *
 * finished = a "promise" that is fulfilled when FlexSim says "the simulation is complete"
 *            (FlexSim calls /api/flexsim/completeSimulation on the worker: W4).
 *            SimulationJobRunner waits for this OR for FlexSim to close OR for the time limit.
 */
public final class ActiveRun {

    static final String DEFAULT_FINISHED_MESSAGE = "Simulation finished";

    private final long runId;
    private final long experimentId;
    private final CompletableFuture<String> finished = new CompletableFuture<>();

    ActiveRun(long runId, long experimentId) {
        this.runId = runId;
        this.experimentId = experimentId;
    }

    public long runId() {
        return runId;
    }

    public long experimentId() {
        return experimentId;
    }

    /** Called when FlexSim reports "complete". Only the FIRST call counts. */
    public boolean markFinished(String message) {
        return finished.complete(message == null || message.isBlank() ? DEFAULT_FINISHED_MESSAGE : message);
    }

    CompletableFuture<String> finished() {
        return finished;
    }
}

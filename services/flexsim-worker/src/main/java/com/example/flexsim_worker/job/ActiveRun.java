package com.example.flexsim_worker.job;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * ONE run that is going on right now on this worker.
 *
 * outcome = a "promise" that is fulfilled ONCE, by the first of:
 *   markFinished(...) FlexSim called /api/flexsim/completeSimulation    -> completed = true
 *   markFailed(...)   the worker can not go on (data could not be loaded,
 *                     or the worker is shutting down)                    -> completed = false
 * SimulationJobRunner waits for this OR for FlexSim to close OR for the time limit.
 */
public final class ActiveRun {

    /** How the run ended, as decided by FlexSim or by the worker. */
    public record Outcome(boolean completed, String message) {
    }

    static final String DEFAULT_FINISHED_MESSAGE = "Simulation finished";
    static final String WORKER_STOPPED_MESSAGE =
            "The simulation worker was stopped during this run. Please start the simulation again.";

    private final long runId;
    private final long experimentId;
    private final CompletableFuture<Outcome> outcome = new CompletableFuture<>();
    private volatile Process process;

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

    /** FlexSim reported "complete". Only the FIRST call (finished OR failed) counts. */
    public boolean markFinished(String message) {
        return outcome.complete(new Outcome(true,
                message == null || message.isBlank() ? DEFAULT_FINISHED_MESSAGE : message));
    }

    /** The worker can not continue this run. Only the FIRST call (finished OR failed) counts. */
    public boolean markFailed(String message) {
        return outcome.complete(new Outcome(false, message));
    }

    CompletableFuture<Outcome> outcome() {
        return outcome;
    }

    /** The outcome, if it is already decided (empty while the run is still going on). */
    public Optional<Outcome> result() {
        return Optional.ofNullable(outcome.getNow(null));
    }

    void attach(Process process) {
        this.process = process;
    }

    Optional<Process> process() {
        return Optional.ofNullable(process);
    }
}

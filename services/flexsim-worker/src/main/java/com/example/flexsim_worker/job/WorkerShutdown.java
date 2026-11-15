package com.example.flexsim_worker.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * When the worker STOPS (Windows update, restart, "Stop service"): close every FlexSim cleanly.
 *
 * Without this, FlexSim would keep running with nobody watching it: it would hold a licence and
 * the run would stay RUNNING for hours.
 *
 * How: every active run is marked failed ("worker was stopped"). Each run thread then closes its
 * FlexSim and reports FAILED to simulation-service (the user sees it and can start again).
 *
 * SmartLifecycle + phase MAX_VALUE = this stop() runs FIRST, before the Kafka listeners stop.
 * So the run threads can finish properly while Kafka is still connected.
 */
@Component
public class WorkerShutdown implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(WorkerShutdown.class);

    private final ActiveRuns activeRuns;
    private volatile boolean running;

    public WorkerShutdown(ActiveRuns activeRuns) {
        this.activeRuns = activeRuns;
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        for (ActiveRun run : activeRuns.all()) {
            if (run.markFailed(ActiveRun.WORKER_STOPPED_MESSAGE)) {
                log.warn("Worker is stopping: closing FlexSim of run {} (experiment {})", run.runId(), run.experimentId());
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}

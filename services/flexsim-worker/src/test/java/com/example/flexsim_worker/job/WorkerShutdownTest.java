package com.example.flexsim_worker.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkerShutdownTest {

    @Test
    void stoppingTheWorkerEndsEveryActiveRun() {
        ActiveRuns runs = new ActiveRuns();
        ActiveRun first = runs.start(7, 42);
        ActiveRun second = runs.start(8, 43);
        WorkerShutdown shutdown = new WorkerShutdown(runs);
        shutdown.start();

        shutdown.stop();

        assertThat(shutdown.isRunning()).isFalse();
        assertThat(first.outcome().join()).isEqualTo(new ActiveRun.Outcome(false, ActiveRun.WORKER_STOPPED_MESSAGE));
        assertThat(second.outcome().join().completed()).isFalse();
    }

    @Test
    void aRunThatIsAlreadyCompleteStaysComplete() {
        ActiveRuns runs = new ActiveRuns();
        ActiveRun run = runs.start(7, 42);
        run.markFinished("done");

        new WorkerShutdown(runs).stop();

        assertThat(run.outcome().join()).isEqualTo(new ActiveRun.Outcome(true, "done"));
    }
}

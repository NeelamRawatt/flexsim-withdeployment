package com.example.flexsim_worker.job;

import com.example.flexsim_worker.client.RunStatus;
import com.example.flexsim_worker.client.RunView;
import com.example.flexsim_worker.client.SimulationClient;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Sends the run's status to simulation-service, with two different levels of "how important":
 *
 *   progress(...)  "RUNNING, stage X"   -> ONE try. If it fails, only a warning: the next stage
 *                                          update will show the progress anyway.
 *   finish(...)    COMPLETED / FAILED   -> MUST arrive: up to 6 tries, waiting 2, 4, 8, 16, 30 s
 *                                          (about 1 minute), so a short restart of
 *                                          simulation-service does not lose the result.
 *
 * finish() never throws: the run is over, nothing in the job may start again because of a failed report.
 */
@Component
public class StatusReporter {

    private static final Logger log = LoggerFactory.getLogger(StatusReporter.class);

    static final String FINISHED_STAGE = "FINISHED";
    static final long[] WAITS_MS = {2_000, 4_000, 8_000, 16_000, 30_000};

    /** "wait this long". Tests give a version that does not really wait. */
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final SimulationClient simulationClient;
    private final Sleeper sleeper;

    @Autowired
    public StatusReporter(SimulationClient simulationClient) {
        this(simulationClient, Thread::sleep);
    }

    StatusReporter(SimulationClient simulationClient, Sleeper sleeper) {
        this.simulationClient = simulationClient;
        this.sleeper = sleeper;
    }

    public void progress(long runId, String stage, String message) {
        try {
            simulationClient.updateStatus(runId, RunStatus.RUNNING, stage, message);
        } catch (RuntimeException e) {
            log.warn("Run {}: could not report stage {} ({})", runId, stage, e.getMessage());
        }
    }

    /** @return true = simulation-service has the final status */
    public boolean finish(long runId, RunStatus status, String message) {
        for (int attempt = 0; ; attempt++) {
            try {
                Optional<RunView> answer = simulationClient.updateStatus(runId, status, FINISHED_STAGE, message);
                if (answer.isEmpty()) {
                    log.info("Run {}: not reported as {}, the run no longer exists (experiment deleted)", runId, status);
                    return false;
                }
                log.info("Run {}: reported {} ({})", runId, status, message);
                return true;
            } catch (HttpClientErrorException.Conflict e) {
                // The run is already final (for example someone else marked it). Nothing more to do.
                log.warn("Run {}: simulation-service did not accept {} (the run is already finished)", runId, status);
                return false;
            } catch (RuntimeException e) {
                if (attempt >= WAITS_MS.length) {
                    log.error("Run {}: GAVE UP reporting {} after {} tries ({}). The run stays RUNNING in "
                            + "simulation-service and needs a manual check.", runId, status, attempt + 1, e.getMessage());
                    return false;
                }
                log.warn("Run {}: reporting {} failed ({}), trying again in {} s",
                        runId, status, e.getMessage(), WAITS_MS[attempt] / 1000);
                try {
                    sleeper.sleep(WAITS_MS[attempt]);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    log.error("Run {}: worker stopped while reporting {}", runId, status);
                    return false;
                }
            }
        }
    }
}

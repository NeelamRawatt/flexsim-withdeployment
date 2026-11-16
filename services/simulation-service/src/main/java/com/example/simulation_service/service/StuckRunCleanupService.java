package com.example.simulation_service.service;

import com.example.simulation_service.dto.RunStatusUpdateRequest;
import com.example.simulation_service.entity.SimulationRun;
import com.example.simulation_service.enums.RunStatus;
import com.example.simulation_service.repository.SimulationRunRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Every 15 minutes: find runs that are STUCK and mark them FAILED, so the user is never left
 * waiting for ever and can start the experiment again.
 *
 * When does a run get stuck? Only when something outside is broken:
 *   RUNNING for more than 7 hours -> the worker gives up after 6 hours (its time limit) and reports
 *                                    FAILED itself. No report 1 hour later = the worker VM died or lost
 *                                    its network for good.
 *   QUEUED for more than 24 hours -> no worker ever took the job (all worker VMs down, or the job
 *                                    landed in the "failed messages" topic).
 *
 * It uses the NORMAL status change (SimulationRunService.updateStatus), so everything else stays
 * correct: the outbox sends simulation.status, experiment-service shows FAILED, the live screen updates.
 * With 2+ copies of simulation-service both may find the same run: the @Version check lets only
 * ONE of them change it; the other one just logs and moves on.
 */
@Service
public class StuckRunCleanupService {

    private static final Logger log = LoggerFactory.getLogger(StuckRunCleanupService.class);
    static final String STAGE = "TIMED_OUT";

    private final SimulationRunRepository repository;
    private final SimulationRunService runService;
    private final long runningLimitMinutes;
    private final long queuedLimitHours;

    public StuckRunCleanupService(SimulationRunRepository repository, SimulationRunService runService,
                                  @Value("${app.runs.stuck-running-after-minutes:420}") long runningLimitMinutes,
                                  @Value("${app.runs.stuck-queued-after-hours:24}") long queuedLimitHours) {
        this.repository = repository;
        this.runService = runService;
        this.runningLimitMinutes = runningLimitMinutes;
        this.queuedLimitHours = queuedLimitHours;
    }

    @Scheduled(fixedDelayString = "${app.runs.stuck-check-ms:900000}", initialDelayString = "${app.runs.stuck-check-ms:900000}")
    public void failStuckRuns() {
        int failed = failStuckRuns(LocalDateTime.now(ZoneOffset.UTC));
        if (failed > 0) {
            log.warn("Stuck-run check: marked {} run(s) FAILED", failed);
        }
    }

    /** @param now the current time in UTC (a parameter, so a test can choose it) */
    int failStuckRuns(LocalDateTime now) {
        int failed = 0;
        List<SimulationRun> running = repository.findTop100ByStatusAndStartedAtBeforeOrderByStartedAtAsc(
                RunStatus.RUNNING, now.minusMinutes(runningLimitMinutes));
        for (SimulationRun run : running) {
            failed += fail(run, "The simulation worker stopped answering during this run. Please start the simulation again.");
        }
        List<SimulationRun> queued = repository.findTop100ByStatusAndQueuedAtBeforeOrderByQueuedAtAsc(
                RunStatus.QUEUED, now.minusHours(queuedLimitHours));
        for (SimulationRun run : queued) {
            failed += fail(run, "No simulation worker started this run. Please start the simulation again.");
        }
        return failed;
    }

    private int fail(SimulationRun run, String message) {
        try {
            runService.updateStatus(run.getId(), new RunStatusUpdateRequest(RunStatus.FAILED, STAGE, message));
            log.warn("Run {} of experiment {} was {} for too long: marked FAILED", run.getId(), run.getExperimentId(),
                    run.getStatus());
            return 1;
        } catch (ObjectOptimisticLockingFailureException | ResponseStatusException e) {
            // Changed at the same moment (the worker's late report, or another copy of this service). Fine.
            log.info("Run {} changed while the stuck-run check looked at it; skipped ({})", run.getId(), e.getMessage());
            return 0;
        }
    }
}

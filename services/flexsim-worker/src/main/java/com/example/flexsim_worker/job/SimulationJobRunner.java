package com.example.flexsim_worker.job;

import com.example.flexsim_worker.client.RunStatus;
import com.example.flexsim_worker.client.RunView;
import com.example.flexsim_worker.client.SimulationClient;
import com.example.flexsim_worker.event.SimulationTriggerRequestedEvent;
import com.example.flexsim_worker.flexsim.FlexsimLauncher;
import com.example.flexsim_worker.flexsim.FlexsimSettings;
import com.example.flexsim_worker.flexsim.RunFiles;
import com.example.flexsim_worker.flexsim.ShiftTimes;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Does ONE job from start to end. Called by SimulationJobListener (one Kafka thread = one run).
 *
 *  1. Ask simulation-service: what is the run's status NOW?
 *       does not exist  -> experiment was deleted          -> skip
 *       CANCELLED / COMPLETED / FAILED                     -> skip (cancelled, or a duplicate message)
 *       RUNNING         -> a worker crashed during this run and Kafka gave the job again
 *                          -> mark FAILED ("please start again"), because we can not continue a half run
 *       QUEUED          -> go on
 *  2. Read the shift times (night shift = end on the next day). Not valid -> FAILED.
 *  3. Tell simulation-service: RUNNING, stage OPENING_FLEXSIM.  (409 = cancelled meanwhile -> skip)
 *     ---- from here on this class NEVER throws: it reports FAILED itself. So a Kafka retry
 *          can never start FlexSim a second time for the same run. ----
 *  4. Prepare the run folder (model copy + script.txt) and START FlexSim (ProcessBuilder).
 *  5. WAIT for the FIRST of these three:
 *       FlexSim says "complete" (W4)  -> COMPLETED, then let FlexSim close (frees the licence)
 *       FlexSim closes by itself       -> FAILED  (it stopped before the end)
 *       the time limit (6 h)           -> close FlexSim -> FAILED
 *
 * Steps 1 and 3 may throw if simulation-service is down. That is OK: nothing has started yet,
 * so the Kafka error handler simply tries the job again a bit later.
 */
@Component
public class SimulationJobRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulationJobRunner.class);
    private static final Duration STOP_WAIT = Duration.ofSeconds(10);

    private final SimulationClient simulationClient;
    private final StatusReporter reporter;
    private final FlexsimLauncher launcher;
    private final ActiveRuns activeRuns;
    private final Duration maxRunTime;
    private final Duration exitGrace;
    private final String workerName;

    @Autowired
    public SimulationJobRunner(SimulationClient simulationClient, StatusReporter reporter, FlexsimLauncher launcher,
                               ActiveRuns activeRuns, FlexsimSettings settings) {
        this(simulationClient, reporter, launcher, activeRuns,
                Duration.ofMinutes(settings.maxRunMinutes()), Duration.ofSeconds(settings.exitGraceSeconds()));
    }

    SimulationJobRunner(SimulationClient simulationClient, StatusReporter reporter, FlexsimLauncher launcher,
                        ActiveRuns activeRuns, Duration maxRunTime, Duration exitGrace) {
        this.simulationClient = simulationClient;
        this.reporter = reporter;
        this.launcher = launcher;
        this.activeRuns = activeRuns;
        this.maxRunTime = maxRunTime;
        this.exitGrace = exitGrace;
        this.workerName = hostName();
    }

    public void run(SimulationTriggerRequestedEvent job) {
        long runId = job.runId();
        long experimentId = job.experimentId();

        // ---- 1. is the run still waiting? ----
        Optional<RunView> current = simulationClient.getRun(runId);
        if (current.isEmpty()) {
            log.info("Run {}: does not exist any more (experiment deleted). Job skipped.", runId);
            return;
        }
        RunStatus status = current.get().status();
        if (status != null && status.isFinal()) {
            log.info("Run {}: already {}. Job skipped.", runId, status);
            return;
        }
        if (status == RunStatus.RUNNING) {
            log.warn("Run {}: is RUNNING already, so a worker stopped during this run. Marking it FAILED.", runId);
            reporter.finish(runId, RunStatus.FAILED,
                    "The simulation worker restarted during this run. Please start the simulation again.");
            return;
        }

        // ---- 2. shift times ----
        ShiftTimes.Shift shift;
        try {
            shift = ShiftTimes.parse(job.startTime(), job.endTime());
        } catch (IllegalArgumentException e) {
            reporter.finish(runId, RunStatus.FAILED, "The experiment times are not valid: " + e.getMessage());
            return;
        }

        // ---- 3. claim the run ----
        try {
            if (simulationClient.updateStatus(runId, RunStatus.RUNNING, "OPENING_FLEXSIM",
                    "Opening FlexSim on " + workerName).isEmpty()) {
                log.info("Run {}: deleted just now. Job skipped.", runId);
                return;
            }
        } catch (HttpClientErrorException.Conflict e) {
            log.info("Run {}: was changed meanwhile (for example cancelled). Job skipped.", runId);
            return;
        }

        // ---- 4 + 5: from here on, never throw ----
        ActiveRun active = null;
        try {
            active = activeRuns.start(runId, experimentId);
            execute(job, shift, active);
        } catch (RuntimeException e) {
            log.error("Run {}: unexpected worker error", runId, e);
            reporter.finish(runId, RunStatus.FAILED, "Unexpected error in the simulation worker: " + e.getMessage());
        } finally {
            if (active != null) {
                activeRuns.remove(active);
            }
        }
    }

    private void execute(SimulationTriggerRequestedEvent job, ShiftTimes.Shift shift, ActiveRun active) {
        long runId = active.runId();

        RunFiles files;
        try {
            files = launcher.prepare(job, shift);
        } catch (IOException e) {
            log.error("Run {}: could not prepare the run folder", runId, e);
            reporter.finish(runId, RunStatus.FAILED, "Could not prepare the simulation files: " + e.getMessage());
            return;
        }

        Process process;
        try {
            process = launcher.start(files);
        } catch (IOException e) {
            log.error("Run {}: FlexSim could not be started", runId, e);
            reporter.finish(runId, RunStatus.FAILED, "FlexSim could not be started: " + e.getMessage());
            return;
        }
        log.info("Run {}: FlexSim started (experiment {}, folder {})", runId, active.experimentId(), files.folder());
        reporter.progress(runId, "PREPARING_IMPORT", "FlexSim started");

        // ---- 5. wait for: complete / FlexSim closed / time limit ----
        try {
            CompletableFuture.anyOf(process.onExit(), active.finished())
                    .get(maxRunTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("Run {}: time limit of {} minutes reached, stopping FlexSim", runId, maxRunTime.toMinutes());
            stop(process);
            reporter.finish(runId, RunStatus.FAILED,
                    "The simulation took longer than " + maxRunTime.toMinutes() + " minutes and was stopped.");
            return;
        } catch (InterruptedException e) {
            // The worker itself is shutting down (for example a Windows update restarts the VM).
            Thread.currentThread().interrupt();
            log.warn("Run {}: worker is stopping, closing FlexSim", runId);
            stop(process);
            reporter.finish(runId, RunStatus.FAILED,
                    "The simulation worker was stopped during this run. Please start the simulation again.");
            return;
        } catch (ExecutionException e) {
            log.error("Run {}: error while waiting for FlexSim", runId, e);
            stop(process);
            reporter.finish(runId, RunStatus.FAILED, "Error while waiting for FlexSim: " + e.getMessage());
            return;
        }

        if (active.finished().isDone()) {
            reporter.finish(runId, RunStatus.COMPLETED, active.finished().getNow(ActiveRun.DEFAULT_FINISHED_MESSAGE));
            closeAfterFinish(runId, process);
        } else {
            int exitCode = exitCode(process);
            log.warn("Run {}: FlexSim closed before the end (exit code {}). See {}", runId, exitCode, files.log());
            reporter.finish(runId, RunStatus.FAILED, "FlexSim closed before the simulation finished (exit code "
                    + exitCode + "). The worker log file flexsim-output.log has the details.");
        }
    }

    /** After "complete": give FlexSim time to close by itself, else close it (it holds a licence). */
    private void closeAfterFinish(long runId, Process process) {
        try {
            process.onExit().get(exitGrace.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.info("Run {}: FlexSim still open {} s after finishing, closing it", runId, exitGrace.toSeconds());
            stop(process);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stop(process);
        } catch (ExecutionException e) {
            stop(process);
        }
    }

    /** Close FlexSim (and any program it started), first politely, then by force. */
    static void stop(Process process) {
        try {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (UnsupportedOperationException ignored) {
            // some Process types can not list their children
        }
        process.destroy();
        try {
            if (!process.waitFor(STOP_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private static int exitCode(Process process) {
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException e) {
            return -1;
        }
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            return "the worker";
        }
    }
}

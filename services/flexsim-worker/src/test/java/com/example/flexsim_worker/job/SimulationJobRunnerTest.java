package com.example.flexsim_worker.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.flexsim_worker.TestJobs;
import com.example.flexsim_worker.client.RunStatus;
import com.example.flexsim_worker.client.RunView;
import com.example.flexsim_worker.client.SimulationClient;
import com.example.flexsim_worker.flexsim.FlexsimLauncher;
import com.example.flexsim_worker.flexsim.RunFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Tests the whole job flow with FAKES: a fake simulation-service (Mockito), a fake FlexSim (FakeProcess).
 * Time limits are tiny here (milliseconds), so the tests are fast.
 */
class SimulationJobRunnerTest {

    private static final long RUN = 7L;
    private static final long EXP = 42L;
    private static final RunFiles FILES = new RunFiles(Path.of("run-7"), Path.of("run-7/model.fsm"),
            Path.of("run-7/script.txt"), Path.of("run-7/flexsim-output.log"));

    private SimulationClient simulationClient;
    private StatusReporter reporter;
    private FlexsimLauncher launcher;
    private ActiveRuns activeRuns;
    private SimulationJobRunner runner;
    private FakeProcess flexsim;

    @BeforeEach
    void setUp() throws Exception {
        simulationClient = mock(SimulationClient.class);
        reporter = mock(StatusReporter.class);
        launcher = mock(FlexsimLauncher.class);
        activeRuns = new ActiveRuns();
        runner = new SimulationJobRunner(simulationClient, reporter, launcher, activeRuns,
                Duration.ofMillis(300), Duration.ofMillis(100));
        flexsim = new FakeProcess();
        when(launcher.prepare(any(), any())).thenReturn(FILES);
    }

    private void runIs(RunStatus status) {
        when(simulationClient.getRun(RUN)).thenReturn(Optional.of(new RunView(RUN, EXP, status, null, null)));
    }

    private void claimWorks() {
        when(simulationClient.updateStatus(eq(RUN), eq(RunStatus.RUNNING), eq("OPENING_FLEXSIM"), anyString()))
                .thenReturn(Optional.of(new RunView(RUN, EXP, RunStatus.RUNNING, "OPENING_FLEXSIM", null)));
    }

    @Test
    void happyPathFlexsimSaysCompleteThenIsClosed() throws Exception {
        runIs(RunStatus.QUEUED);
        claimWorks();
        when(launcher.start(FILES)).thenAnswer(call -> {
            // FlexSim reports "complete" (in W4 this comes from the localhost API)
            activeRuns.findByExperiment(EXP).orElseThrow().markFinished("Simulation completed");
            return flexsim;
        });

        runner.run(TestJobs.nightShift(RUN, EXP));

        verify(reporter).progress(RUN, "PREPARING_IMPORT", "FlexSim started");
        verify(reporter).finish(RUN, RunStatus.COMPLETED, "Simulation completed");
        assertThat(flexsim.destroyed).as("FlexSim closed to free the licence").isTrue();
        assertThat(activeRuns.count()).isZero();
    }

    @Test
    void workerSaysCanNotGoOnSoFlexsimIsClosedAndFailed() throws Exception {
        runIs(RunStatus.QUEUED);
        claimWorks();
        when(launcher.start(FILES)).thenAnswer(call -> {
            // for example the parcel data could not be loaded (the localhost API marks the run failed)
            activeRuns.findByExperiment(EXP).orElseThrow().markFailed("Could not load the parcel data for FlexSim.");
            return flexsim;
        });

        runner.run(TestJobs.nightShift(RUN, EXP));

        assertThat(flexsim.destroyed).isTrue();
        verify(reporter).finish(RUN, RunStatus.FAILED, "Could not load the parcel data for FlexSim.");
        assertThat(activeRuns.count()).isZero();
    }

    @Test
    void flexsimClosingBeforeTheEndIsFailed() throws Exception {
        runIs(RunStatus.QUEUED);
        claimWorks();
        flexsim.exitNow(3);
        when(launcher.start(FILES)).thenReturn(flexsim);

        runner.run(TestJobs.nightShift(RUN, EXP));

        verify(reporter).finish(eq(RUN), eq(RunStatus.FAILED), contains("exit code 3"));
        verify(reporter, never()).finish(eq(RUN), eq(RunStatus.COMPLETED), anyString());
    }

    @Test
    void timeLimitStopsFlexsimAndFails() throws Exception {
        runIs(RunStatus.QUEUED);
        claimWorks();
        when(launcher.start(FILES)).thenReturn(flexsim);   // never finishes, never closes

        runner.run(TestJobs.nightShift(RUN, EXP));

        assertThat(flexsim.destroyed).isTrue();
        verify(reporter).finish(eq(RUN), eq(RunStatus.FAILED), contains("took longer than"));
        assertThat(activeRuns.count()).isZero();
    }

    @Test
    void flexsimThatCanNotStartIsFailed() throws Exception {
        runIs(RunStatus.QUEUED);
        claimWorks();
        when(launcher.start(FILES)).thenThrow(new IOException("flexsim.exe not found"));

        runner.run(TestJobs.nightShift(RUN, EXP));

        verify(reporter).finish(eq(RUN), eq(RunStatus.FAILED), contains("flexsim.exe not found"));
        assertThat(activeRuns.count()).isZero();
    }

    @Test
    void deletedRunIsSkipped() {
        when(simulationClient.getRun(RUN)).thenReturn(Optional.empty());

        runner.run(TestJobs.nightShift(RUN, EXP));

        verifyNoInteractions(launcher, reporter);
    }

    @Test
    void cancelledOrDuplicateJobIsSkipped() {
        runIs(RunStatus.CANCELLED);

        runner.run(TestJobs.nightShift(RUN, EXP));

        verifyNoInteractions(launcher, reporter);
        verify(simulationClient, never()).updateStatus(eq(RUN), any(), any(), any());
    }

    @Test
    void runAlreadyRunningMeansAWorkerCrashedSoItFails() {
        runIs(RunStatus.RUNNING);

        runner.run(TestJobs.nightShift(RUN, EXP));

        verify(reporter).finish(eq(RUN), eq(RunStatus.FAILED), contains("restarted"));
        verifyNoInteractions(launcher);
    }

    @Test
    void cancelledJustBeforeTheClaimIsSkipped() {
        runIs(RunStatus.QUEUED);
        when(simulationClient.updateStatus(eq(RUN), eq(RunStatus.RUNNING), anyString(), anyString()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", new HttpHeaders(), new byte[0], null));

        runner.run(TestJobs.nightShift(RUN, EXP));

        verifyNoInteractions(launcher, reporter);
    }

    @Test
    void invalidTimesFailWithoutStartingFlexsim() {
        runIs(RunStatus.QUEUED);
        var job = new com.example.flexsim_worker.event.SimulationTriggerRequestedEvent("e", RUN, EXP, "x",
                "2026-10-21T08:00:00", "2026-10-21T08:00:00", 3,
                null, null, null, null, null, null, null, null, "neelam", "now");

        runner.run(job);

        verify(reporter).finish(eq(RUN), eq(RunStatus.FAILED), contains("times are not valid"));
        verifyNoInteractions(launcher);
    }
}

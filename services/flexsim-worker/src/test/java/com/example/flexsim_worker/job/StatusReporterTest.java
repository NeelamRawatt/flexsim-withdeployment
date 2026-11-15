package com.example.flexsim_worker.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.flexsim_worker.client.RunStatus;
import com.example.flexsim_worker.client.RunView;
import com.example.flexsim_worker.client.SimulationClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

class StatusReporterTest {

    private static final RunView DONE = new RunView(7L, 42L, RunStatus.COMPLETED, "FINISHED", "ok");

    private SimulationClient client;
    private final List<Long> waits = new ArrayList<>();
    private StatusReporter reporter;

    @BeforeEach
    void setUp() {
        client = mock(SimulationClient.class);
        reporter = new StatusReporter(client, waits::add);   // records the waits instead of really waiting
    }

    @Test
    void finalStatusIsRetriedUntilItArrives() {
        when(client.updateStatus(7L, RunStatus.COMPLETED, "FINISHED", "ok"))
                .thenThrow(new ResourceAccessException("simulation-service down"))
                .thenThrow(new ResourceAccessException("simulation-service down"))
                .thenReturn(Optional.of(DONE));

        assertThat(reporter.finish(7L, RunStatus.COMPLETED, "ok")).isTrue();
        assertThat(waits).containsExactly(2_000L, 4_000L);
    }

    @Test
    void finalStatusGivesUpAfterAllTries() {
        when(client.updateStatus(anyLong(), any(), anyString(), anyString()))
                .thenThrow(new ResourceAccessException("simulation-service down"));

        assertThat(reporter.finish(7L, RunStatus.FAILED, "x")).isFalse();
        verify(client, times(StatusReporter.WAITS_MS.length + 1)).updateStatus(7L, RunStatus.FAILED, "FINISHED", "x");
    }

    @Test
    void alreadyFinalRunIsNotRetried() {
        when(client.updateStatus(anyLong(), any(), anyString(), anyString()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", new HttpHeaders(), new byte[0], null));

        assertThat(reporter.finish(7L, RunStatus.FAILED, "x")).isFalse();
        assertThat(waits).isEmpty();
    }

    @Test
    void progressProblemIsOnlyAWarning() {
        when(client.updateStatus(eq(7L), eq(RunStatus.RUNNING), anyString(), anyString()))
                .thenThrow(new ResourceAccessException("simulation-service down"));

        reporter.progress(7L, "PREPARING_IMPORT", "FlexSim started");   // does not throw

        assertThat(waits).isEmpty();
    }
}

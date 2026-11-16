package com.example.simulation_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.simulation_service.dto.RunStatusUpdateRequest;
import com.example.simulation_service.entity.SimulationRun;
import com.example.simulation_service.enums.RunStatus;
import com.example.simulation_service.repository.SimulationRunRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** Unit test of the stuck-run check. The database and the run service are fakes (Mockito). */
@ExtendWith(MockitoExtension.class)
class StuckRunCleanupServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 12, 0);

    @Mock
    private SimulationRunRepository repository;
    @Mock
    private SimulationRunService runService;

    private StuckRunCleanupService service;

    @BeforeEach
    void setUp() {
        service = new StuckRunCleanupService(repository, runService, 420, 24);
    }

    private static SimulationRun run(long id, RunStatus status) {
        SimulationRun run = new SimulationRun();
        ReflectionTestUtils.setField(run, "id", id);   // the id is normally made by the database
        run.setExperimentId(42L);
        run.setStatus(status);
        return run;
    }

    @Test
    void stuckRunningAndQueuedRunsAreFailedWithTheRightLimits() {
        when(repository.findTop100ByStatusAndStartedAtBeforeOrderByStartedAtAsc(RunStatus.RUNNING, NOW.minusMinutes(420)))
                .thenReturn(List.of(run(7, RunStatus.RUNNING)));
        when(repository.findTop100ByStatusAndQueuedAtBeforeOrderByQueuedAtAsc(RunStatus.QUEUED, NOW.minusHours(24)))
                .thenReturn(List.of(run(8, RunStatus.QUEUED)));

        int failed = service.failStuckRuns(NOW);

        assertThat(failed).isEqualTo(2);
        ArgumentCaptor<RunStatusUpdateRequest> update = ArgumentCaptor.forClass(RunStatusUpdateRequest.class);
        verify(runService).updateStatus(eq(7L), update.capture());
        assertThat(update.getValue().status()).isEqualTo(RunStatus.FAILED);
        assertThat(update.getValue().stage()).isEqualTo("TIMED_OUT");
        assertThat(update.getValue().message()).contains("stopped answering");
        verify(runService).updateStatus(eq(8L), any());
    }

    @Test
    void nothingStuckChangesNothing() {
        when(repository.findTop100ByStatusAndStartedAtBeforeOrderByStartedAtAsc(any(), any())).thenReturn(List.of());
        when(repository.findTop100ByStatusAndQueuedAtBeforeOrderByQueuedAtAsc(any(), any())).thenReturn(List.of());

        assertThat(service.failStuckRuns(NOW)).isZero();
        verify(runService, never()).updateStatus(any(), any());
    }

    @Test
    void aRunThatFinishedAtTheSameMomentIsSkipped() {
        when(repository.findTop100ByStatusAndStartedAtBeforeOrderByStartedAtAsc(any(), any()))
                .thenReturn(List.of(run(7, RunStatus.RUNNING)));
        when(repository.findTop100ByStatusAndQueuedAtBeforeOrderByQueuedAtAsc(any(), any())).thenReturn(List.of());
        when(runService.updateStatus(eq(7L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Run 7 is COMPLETED"));

        assertThat(service.failStuckRuns(NOW)).isZero();   // no error: the worker's late report won
    }
}

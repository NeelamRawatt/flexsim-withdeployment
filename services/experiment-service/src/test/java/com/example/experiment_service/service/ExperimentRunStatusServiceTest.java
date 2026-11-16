package com.example.experiment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.experiment_service.consumer.InvalidEventException;
import com.example.experiment_service.entity.Experiment;
import com.example.experiment_service.enums.ExperimentRunStatus;
import com.example.experiment_service.event.SimulationStatusEvent;
import com.example.experiment_service.repository.ExperimentRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit test of "copy the run status onto the experiment". The database is a fake (Mockito). */
@ExtendWith(MockitoExtension.class)
class ExperimentRunStatusServiceTest {

    @Mock
    private ExperimentRepository repository;

    private ExperimentRunStatusService service;
    private Experiment experiment;

    @BeforeEach
    void setUp() {
        service = new ExperimentRunStatusService(repository);
        experiment = new Experiment();
        experiment.setExperimentId(42L);
    }

    private static SimulationStatusEvent event(long runId, String status, String stage, String at) {
        return new SimulationStatusEvent("e", runId, 42L, status, stage, "msg", at);
    }

    @Test
    void queuedThenRunningThenCompleted() {
        when(repository.findById(42L)).thenReturn(Optional.of(experiment));

        assertThat(service.apply(event(7, "QUEUED", null, "2026-11-16T07:00:00Z"))).isTrue();
        assertThat(experiment.getCurrentRunStatus()).isEqualTo(ExperimentRunStatus.RUNNING);
        assertThat(experiment.getCurrentRunStage()).isEqualTo("QUEUED");
        assertThat(experiment.getActiveRunId()).isEqualTo(7L);

        assertThat(service.apply(event(7, "RUNNING", "IMPORTING_PARCEL_DATA", "2026-11-16T07:01:00Z"))).isTrue();
        assertThat(experiment.getCurrentRunStage()).isEqualTo("IMPORTING_PARCEL_DATA");

        assertThat(service.apply(event(7, "COMPLETED", "FINISHED", "2026-11-16T09:00:00Z"))).isTrue();
        assertThat(experiment.getCurrentRunStatus()).isEqualTo(ExperimentRunStatus.COMPLETED);
        assertThat(experiment.getCurrentRunUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 16, 9, 0));
    }

    @Test
    void finishedRunNeverGoesBackToRunning() {
        when(repository.findById(42L)).thenReturn(Optional.of(experiment));
        service.apply(event(7, "COMPLETED", "FINISHED", "2026-11-16T09:00:00Z"));

        assertThat(service.apply(event(7, "RUNNING", "LATE", "2026-11-16T08:00:00Z"))).isFalse();
        assertThat(experiment.getCurrentRunStatus()).isEqualTo(ExperimentRunStatus.COMPLETED);
    }

    @Test
    void lateMessageOfAnOlderRunIsSkipped() {
        when(repository.findById(42L)).thenReturn(Optional.of(experiment));
        service.apply(event(8, "RUNNING", "OPENING_FLEXSIM", "2026-11-16T10:00:00Z"));

        assertThat(service.apply(event(7, "FAILED", null, "2026-11-16T10:05:00Z"))).isFalse();
        assertThat(experiment.getActiveRunId()).isEqualTo(8L);
        assertThat(experiment.getCurrentRunStatus()).isEqualTo(ExperimentRunStatus.RUNNING);
    }

    @Test
    void olderMessageOfTheSameRunIsSkipped() {
        when(repository.findById(42L)).thenReturn(Optional.of(experiment));
        service.apply(event(7, "RUNNING", "SIMULATION_STARTED", "2026-11-16T07:10:00Z"));

        assertThat(service.apply(event(7, "RUNNING", "OPENING_FLEXSIM", "2026-11-16T07:01:00Z"))).isFalse();
        assertThat(experiment.getCurrentRunStage()).isEqualTo("SIMULATION_STARTED");
    }

    @Test
    void cancelledIsShownAsTerminatedAndANewRunCanStartAfterIt() {
        when(repository.findById(42L)).thenReturn(Optional.of(experiment));
        service.apply(event(7, "CANCELLED", null, "2026-11-16T07:00:00Z"));
        assertThat(experiment.getCurrentRunStatus()).isEqualTo(ExperimentRunStatus.TERMINATED);

        assertThat(service.apply(event(8, "QUEUED", null, "2026-11-16T07:05:00Z"))).isTrue();
        assertThat(experiment.getCurrentRunStatus()).isEqualTo(ExperimentRunStatus.RUNNING);
    }

    @Test
    void deletedExperimentAndUnknownStatus() {
        when(repository.findById(42L)).thenReturn(Optional.empty());
        assertThat(service.apply(event(7, "RUNNING", null, null))).isFalse();

        assertThatThrownBy(() -> ExperimentRunStatusService.toExperimentStatus("PAUSED"))
                .isInstanceOf(InvalidEventException.class);
    }
}

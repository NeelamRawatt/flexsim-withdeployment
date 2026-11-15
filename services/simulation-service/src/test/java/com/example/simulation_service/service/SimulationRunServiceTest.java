package com.example.simulation_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.simulation_service.client.ExperimentClient;
import com.example.simulation_service.client.ExperimentSummary;
import com.example.simulation_service.client.FileClient;
import com.example.simulation_service.dto.RunStatusUpdateRequest;
import com.example.simulation_service.dto.SimulationRunDto;
import com.example.simulation_service.entity.SimulationRun;
import com.example.simulation_service.enums.RunStatus;
import com.example.simulation_service.event.SimulationStatusEvent;
import com.example.simulation_service.event.SimulationTriggerRequestedEvent;
import com.example.simulation_service.repository.SimulationRunRepository;
import com.example.simulation_service.security.AuthenticatedUser;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

/** Unit test of "start a run". Everything around the service is a fake. */
@ExtendWith(MockitoExtension.class)
class SimulationRunServiceTest {

    private static final AuthenticatedUser NEELAM = new AuthenticatedUser(5L, "neelam", "OPERATOR");
    private static final ExperimentSummary EXPERIMENT = new ExperimentSummary(
            1006L, "neelam", "Pune test", "2026-10-21T23:00:00", "2026-10-22T06:30:00", 3);

    @Mock
    private SimulationRunRepository repository;
    @Mock
    private ExperimentClient experimentClient;
    @Mock
    private FileClient fileClient;
    @Mock
    private OutboxService outboxService;

    private SimulationRunService service;

    /** The test has no database, so "begin/commit" do nothing. */
    static class NoDatabaseTransactions implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }

    @BeforeEach
    void setUp() {
        service = new SimulationRunService(repository, experimentClient, fileClient, outboxService,
                new NoDatabaseTransactions());
    }

    /** Pretends the database gave the row id 42 (the real database does this). */
    private static SimulationRun withId(SimulationRun run) throws Exception {
        Field id = SimulationRun.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(run, 42L);
        return run;
    }

    private static void assertStatus(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }

    @Test
    void aRunIsQueuedAndBothKafkaMessagesGoToTheOutbox() {
        when(experimentClient.requireOwnedBy(1006L, NEELAM, "t")).thenReturn(EXPERIMENT);
        when(fileClient.filesReady(1006L, "t")).thenReturn(true);
        when(repository.existsByExperimentIdAndStatusIn(eq(1006L), anyCollection())).thenReturn(false);
        when(repository.saveAndFlush(any(SimulationRun.class))).thenAnswer(inv -> withId(inv.getArgument(0)));

        SimulationRunDto run = service.start(1006L, NEELAM, "t");

        assertThat(run.runId()).isEqualTo(42L);
        assertThat(run.status()).isEqualTo(RunStatus.QUEUED);
        assertThat(run.requestedBy()).isEqualTo("neelam");

        ArgumentCaptor<Object> job = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).add(eq("simulation.trigger.requested"), eq("1006"), job.capture());
        SimulationTriggerRequestedEvent trigger = (SimulationTriggerRequestedEvent) job.getValue();
        assertThat(trigger.runId()).isEqualTo(42L);
        assertThat(trigger.startTime()).isEqualTo("2026-10-21T23:00:00");
        assertThat(trigger.maxRecirculationCount()).isEqualTo(3);

        ArgumentCaptor<Object> status = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).add(eq("simulation.status"), eq("1006"), status.capture());
        assertThat(((SimulationStatusEvent) status.getValue()).status()).isEqualTo("QUEUED");
    }

    @Test
    void filesNotReadyGives400AndNothingIsSaved() {
        when(experimentClient.requireOwnedBy(1006L, NEELAM, "t")).thenReturn(EXPERIMENT);
        when(fileClient.filesReady(1006L, "t")).thenReturn(false);

        assertStatus(() -> service.start(1006L, NEELAM, "t"), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(repository, outboxService);
    }

    @Test
    void notTheOwnerStopsBeforeTheFilesCheck() {
        when(experimentClient.requireOwnedBy(1006L, NEELAM, "t"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "no"));

        assertStatus(() -> service.start(1006L, NEELAM, "t"), HttpStatus.FORBIDDEN);
        verifyNoInteractions(fileClient, repository, outboxService);
    }

    @Test
    void aSecondRunWhileOneIsActiveGives409() {
        when(experimentClient.requireOwnedBy(1006L, NEELAM, "t")).thenReturn(EXPERIMENT);
        when(fileClient.filesReady(1006L, "t")).thenReturn(true);
        when(repository.existsByExperimentIdAndStatusIn(eq(1006L), anyCollection())).thenReturn(true);

        assertStatus(() -> service.start(1006L, NEELAM, "t"), HttpStatus.CONFLICT);
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(outboxService);
    }

    @Test
    void twoClicksAtTheSameMomentGive409() {
        when(experimentClient.requireOwnedBy(1006L, NEELAM, "t")).thenReturn(EXPERIMENT);
        when(fileClient.filesReady(1006L, "t")).thenReturn(true);
        when(repository.existsByExperimentIdAndStatusIn(eq(1006L), anyCollection())).thenReturn(false);
        when(repository.saveAndFlush(any(SimulationRun.class)))
                .thenThrow(new DataIntegrityViolationException("uk_simulation_run_one_active_per_experiment"));

        assertStatus(() -> service.start(1006L, NEELAM, "t"), HttpStatus.CONFLICT);
    }

    // ------------------------------------------------ status updates (S4)

    private static SimulationRun runIn(RunStatus status) throws Exception {
        SimulationRun run = withId(new SimulationRun());
        run.setExperimentId(1006L);
        run.setStatus(status);
        run.setRequestedBy("neelam");
        return run;
    }

    @Test
    void queuedToRunningSetsStartedAtAndSendsAStatusMessage() throws Exception {
        SimulationRun run = runIn(RunStatus.QUEUED);
        when(repository.findById(42L)).thenReturn(Optional.of(run));
        when(repository.saveAndFlush(run)).thenReturn(run);

        SimulationRunDto result = service.updateStatus(42L,
                new RunStatusUpdateRequest(RunStatus.RUNNING, "OPENING_FLEXSIM", "Opening FlexSim"));

        assertThat(result.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(result.stage()).isEqualTo("OPENING_FLEXSIM");
        assertThat(result.startedAt()).isNotNull();
        ArgumentCaptor<Object> message = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).add(eq("simulation.status"), eq("1006"), message.capture());
        SimulationStatusEvent event = (SimulationStatusEvent) message.getValue();
        assertThat(event.status()).isEqualTo("RUNNING");
        assertThat(event.stage()).isEqualTo("OPENING_FLEXSIM");
        assertThat(event.runId()).isEqualTo(42L);
    }

    @Test
    void runningToCompletedSetsFinishedAt() throws Exception {
        SimulationRun run = runIn(RunStatus.RUNNING);
        when(repository.findById(42L)).thenReturn(Optional.of(run));
        when(repository.saveAndFlush(run)).thenReturn(run);

        SimulationRunDto result = service.updateStatus(42L,
                new RunStatusUpdateRequest(RunStatus.COMPLETED, null, "Simulation finished"));

        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(result.finishedAt()).isNotNull();
    }

    @Test
    void aFinishedRunCannotStartAgain() throws Exception {
        when(repository.findById(42L)).thenReturn(Optional.of(runIn(RunStatus.COMPLETED)));

        assertStatus(() -> service.updateStatus(42L, new RunStatusUpdateRequest(RunStatus.RUNNING, "X", null)),
                HttpStatus.CONFLICT);
        verifyNoInteractions(outboxService);
    }

    @Test
    void queuedCannotJumpToCompleted() throws Exception {
        when(repository.findById(42L)).thenReturn(Optional.of(runIn(RunStatus.QUEUED)));

        assertStatus(() -> service.updateStatus(42L, new RunStatusUpdateRequest(RunStatus.COMPLETED, null, null)),
                HttpStatus.CONFLICT);
    }

    @Test
    void theSameUpdateTwiceIsIgnored() throws Exception {
        SimulationRun run = runIn(RunStatus.COMPLETED);
        when(repository.findById(42L)).thenReturn(Optional.of(run));

        service.updateStatus(42L, new RunStatusUpdateRequest(RunStatus.COMPLETED, null, "again"));

        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(outboxService);   // no duplicate message
    }

    @Test
    void onlyTheStarterOrAdminCanCancelAQueuedRun() throws Exception {
        SimulationRun run = runIn(RunStatus.QUEUED);
        when(repository.findById(42L)).thenReturn(Optional.of(run));

        assertStatus(() -> service.cancel(42L, new AuthenticatedUser(6L, "bob", "OPERATOR")), HttpStatus.FORBIDDEN);

        when(repository.saveAndFlush(run)).thenReturn(run);
        SimulationRunDto result = service.cancel(42L, NEELAM);
        assertThat(result.status()).isEqualTo(RunStatus.CANCELLED);
        assertThat(result.finishedAt()).isNotNull();
        verify(outboxService).add(eq("simulation.status"), eq("1006"), any());
    }

    @Test
    void aRunningRunCannotBeCancelledYet() throws Exception {
        when(repository.findById(42L)).thenReturn(Optional.of(runIn(RunStatus.RUNNING)));

        assertStatus(() -> service.cancel(42L, NEELAM), HttpStatus.CONFLICT);
    }

    @Test
    void unknownRunGives404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertStatus(() -> service.get(99L), HttpStatus.NOT_FOUND);
    }
}
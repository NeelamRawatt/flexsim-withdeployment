package com.example.simulation_service.service;

import com.example.simulation_service.client.ExperimentClient;
import com.example.simulation_service.client.ExperimentSummary;
import com.example.simulation_service.client.FileClient;
import com.example.simulation_service.dto.RunStatusUpdateRequest;
import com.example.simulation_service.dto.SimulationRunDto;
import com.example.simulation_service.entity.SimulationRun;
import com.example.simulation_service.enums.RunStatus;
import com.example.simulation_service.event.KafkaTopics;
import com.example.simulation_service.event.SimulationStatusEvent;
import com.example.simulation_service.event.SimulationTriggerRequestedEvent;
import com.example.simulation_service.repository.SimulationRunRepository;
import com.example.simulation_service.security.AuthenticatedUser;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/**
 * START A RUN, in this order (cheap checks first, the database last):
 *   1. owner check   (experiment-service, REST)  -> 404 / 403 / 503   + reads the run settings
 *   2. files check   (file-service, REST)        -> 400 "files not uploaded" / 503
 *   3. ONE transaction:
 *        - no other QUEUED/RUNNING run for this experiment   (else 409)
 *        - save the run as QUEUED
 *        - outbox: "simulation.trigger.requested" (the job for the worker)
 *        - outbox: "simulation.status" = QUEUED     (so experiment-service shows "Queued")
 *      All saved together, or nothing.
 *
 * The user gets the answer at once ("queued"). The simulation itself runs later, in the worker.
 *
 * STATUS UPDATES (from the flexsim-worker) follow fixed rules, a small "state machine":
 *
 *     QUEUED ──► RUNNING ──► COMPLETED
 *        │          │  ▲ (new stage)
 *        │          ├──┘
 *        ├──────────┴──► FAILED
 *        └──────────┴──► CANCELLED
 *
 *   - a FINAL status (COMPLETED, FAILED, CANCELLED) never changes again        -> 409
 *   - the same update twice (a retry by the worker) is ignored, no error       -> idempotent
 *   - every accepted change writes a "simulation.status" message to the outbox (same transaction)
 */
@Service
public class SimulationRunService {

    private static final Logger log = LoggerFactory.getLogger(SimulationRunService.class);
    private static final List<RunStatus> ACTIVE = List.of(RunStatus.QUEUED, RunStatus.RUNNING);

    /** Which status may follow which. A final status has no next status. */
    private static final Map<RunStatus, Set<RunStatus>> ALLOWED_NEXT = Map.of(
            RunStatus.QUEUED, Set.of(RunStatus.RUNNING, RunStatus.FAILED, RunStatus.CANCELLED),
            RunStatus.RUNNING, Set.of(RunStatus.RUNNING, RunStatus.COMPLETED, RunStatus.FAILED, RunStatus.CANCELLED),
            RunStatus.COMPLETED, Set.of(),
            RunStatus.FAILED, Set.of(),
            RunStatus.CANCELLED, Set.of());

    private final SimulationRunRepository repository;
    private final ExperimentClient experimentClient;
    private final FileClient fileClient;
    private final OutboxService outboxService;
    private final TransactionTemplate transaction;
    private final JsonMapper json = JsonMapper.builder().build();

    public SimulationRunService(SimulationRunRepository repository,
                                ExperimentClient experimentClient,
                                FileClient fileClient,
                                OutboxService outboxService,
                                PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.experimentClient = experimentClient;
        this.fileClient = fileClient;
        this.outboxService = outboxService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public SimulationRunDto start(Long experimentId, AuthenticatedUser user, String token) {
        // 1. exists + yours (+ the settings)
        ExperimentSummary experiment = experimentClient.requireOwnedBy(experimentId, user, token);

        // 2. both Excel files uploaded?
        if (!fileClient.filesReady(experimentId, token)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Required files are not uploaded yet. Please upload the INFEED and ZONE_RESOURCE files first.");
        }

        // 3. save + 2 outbox messages, in ONE transaction
        SimulationRun run;
        try {
            run = transaction.execute(status -> queue(experiment, user));
        } catch (DataIntegrityViolationException e) {
            // Two "Run" clicks at the same moment: the database rule (one active run) stopped the second.
            throw alreadyActive();
        }
        log.info("Run {} queued for experiment {} by {}", run.getId(), experimentId, user.username());
        return SimulationRunDto.from(run);
    }

    private SimulationRun queue(ExperimentSummary experiment, AuthenticatedUser user) {
        Long experimentId = experiment.experimentId();
        if (repository.existsByExperimentIdAndStatusIn(experimentId, ACTIVE)) {
            throw alreadyActive();
        }

        SimulationRun run = new SimulationRun();
        run.setExperimentId(experimentId);
        run.setStatus(RunStatus.QUEUED);
        run.setMessage("Waiting for a free simulation slot");
        run.setRequestedBy(user.username());
        run.setRequestedByUserId(user.userId());
        run.setRunSettings(json.writeValueAsString(experiment));   // exactly what is run, kept for later
        run = repository.saveAndFlush(run);                        // flush: now we have the run id

        String now = Instant.now().toString();
        String key = String.valueOf(experimentId);                 // key = experimentId -> keeps order per experiment
        outboxService.add(KafkaTopics.SIMULATION_TRIGGER_REQUESTED, key, new SimulationTriggerRequestedEvent(
                UUID.randomUUID().toString(), run.getId(), experimentId, experiment.experimentName(),
                experiment.startTime(), experiment.endTime(), experiment.maxRecirculationCount(),
                experiment.useCaseId(), experiment.flexibleResources(), experiment.phoneBoomChute(),
                experiment.dropChuteFlexible(), experiment.flexibleUnloadResources(), experiment.sorterFullPercent(),
                experiment.minimumCageOccupancy(), experiment.maximumCageOccupancy(),
                user.username(), now));
        outboxService.add(KafkaTopics.SIMULATION_STATUS, key, new SimulationStatusEvent(
                UUID.randomUUID().toString(), run.getId(), experimentId, RunStatus.QUEUED.name(),
                null, run.getMessage(), now));
        return run;
    }

    /**
     * A status update from the flexsim-worker. All in ONE transaction: change the run + outbox message.
     * The @Version column stops two updates that arrive at the same moment from overwriting each other
     * (the second one gets 409 and the worker retries).
     */
    @Transactional
    public SimulationRunDto updateStatus(Long runId, RunStatusUpdateRequest update) {
        SimulationRun run = findOrThrow(runId);
        RunStatus from = run.getStatus();
        RunStatus to = update.status();

        // The same update again (example: the worker retried after a timeout): nothing to do.
        boolean sameAgain = from == to
                && (from.isFinal() || (Objects.equals(run.getStage(), update.stage())
                                       && Objects.equals(run.getMessage(), update.message())));
        if (sameAgain) {
            return SimulationRunDto.from(run);
        }
        if (!ALLOWED_NEXT.get(from).contains(to)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Run " + runId + " is " + from + " and cannot change to " + to);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        run.setStatus(to);
        run.setStage(update.stage());
        run.setMessage(update.message());
        if (to == RunStatus.RUNNING && run.getStartedAt() == null) {
            run.setStartedAt(now);
        }
        if (to.isFinal()) {
            run.setFinishedAt(now);
        }
        run = repository.saveAndFlush(run);   // flush now: a clash with another update shows up HERE
        publishStatus(run);
        log.info("Run {} of experiment {}: {} -> {} (stage {})", runId, run.getExperimentId(), from, to, update.stage());
        return SimulationRunDto.from(run);
    }

    /**
     * Cancel a run that is still QUEUED (not started yet). Allowed for the person who started it,
     * or ADMIN / DEVELOPER. (Stopping a RUNNING FlexSim needs the worker's help: a later step.)
     * The worker skips a job whose run is already CANCELLED.
     */
    @Transactional
    public SimulationRunDto cancel(Long runId, AuthenticatedUser user) {
        SimulationRun run = findOrThrow(runId);
        boolean isPowerUser = "ADMIN".equals(user.role()) || "DEVELOPER".equals(user.role());
        if (!isPowerUser && !user.username().equals(run.getRequestedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to cancel this run");
        }
        if (run.getStatus() == RunStatus.CANCELLED) {
            return SimulationRunDto.from(run);   // already cancelled: fine
        }
        if (run.getStatus() != RunStatus.QUEUED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a QUEUED run can be cancelled. This run is " + run.getStatus());
        }
        run.setStatus(RunStatus.CANCELLED);
        run.setMessage("Cancelled by " + user.username());
        run.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
        run = repository.saveAndFlush(run);
        publishStatus(run);
        return SimulationRunDto.from(run);
    }

    private void publishStatus(SimulationRun run) {
        outboxService.add(KafkaTopics.SIMULATION_STATUS, String.valueOf(run.getExperimentId()),
                new SimulationStatusEvent(UUID.randomUUID().toString(), run.getId(), run.getExperimentId(),
                        run.getStatus().name(), run.getStage(), run.getMessage(), Instant.now().toString()));
    }

    private SimulationRun findOrThrow(Long runId) {
        return repository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found with id: " + runId));
    }

    @Transactional(readOnly = true)
    public SimulationRunDto get(Long runId) {
        return repository.findById(runId)
                .map(SimulationRunDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found with id: " + runId));
    }

    @Transactional(readOnly = true)
    public List<SimulationRunDto> listForExperiment(Long experimentId) {
        return repository.findByExperimentIdOrderByQueuedAtDesc(experimentId).stream()
                .map(SimulationRunDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SimulationRunDto latestForExperiment(Long experimentId) {
        return repository.findFirstByExperimentIdOrderByQueuedAtDesc(experimentId)
                .map(SimulationRunDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No simulation run yet for experiment: " + experimentId));
    }

    private static ResponseStatusException alreadyActive() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "A simulation is already queued or running for this experiment.");
    }
}
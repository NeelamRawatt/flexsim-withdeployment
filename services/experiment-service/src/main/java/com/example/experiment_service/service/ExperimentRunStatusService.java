package com.example.experiment_service.service;

import com.example.experiment_service.consumer.InvalidEventException;
import com.example.experiment_service.entity.Experiment;
import com.example.experiment_service.enums.ExperimentRunStatus;
import com.example.experiment_service.event.SimulationStatusEvent;
import com.example.experiment_service.repository.ExperimentRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Copies a run's status onto its experiment ("latest run" fields of the experiment table).
 *
 * Run words -> experiment words:
 *   QUEUED, RUNNING -> RUNNING      (QUEUED shows as stage "QUEUED": the job waits for a FlexSim slot)
 *   COMPLETED       -> COMPLETED
 *   FAILED          -> FAILED
 *   CANCELLED       -> TERMINATED   (the old app's word for "a user stopped it")
 *
 * SAFETY RULES (Kafka can deliver a message twice, and an OLD run's message can still arrive late):
 *   1. experiment deleted                   -> skip
 *   2. message of an OLDER run              -> skip (a new run already started)
 *   3. same run, already finished           -> skip (COMPLETED must never go back to RUNNING)
 *   4. same run, message older than the one we have -> skip
 * Each rule makes the update IDEMPOTENT: the same message twice changes nothing the second time.
 */
@Service
public class ExperimentRunStatusService {

    private static final Logger log = LoggerFactory.getLogger(ExperimentRunStatusService.class);
    static final int MAX_STAGE = 50;
    static final int MAX_MESSAGE = 300;

    private final ExperimentRepository repository;

    public ExperimentRunStatusService(ExperimentRepository repository) {
        this.repository = repository;
    }

    /** @return true = the experiment was changed */
    @Transactional
    public boolean apply(SimulationStatusEvent event) {
        ExperimentRunStatus newStatus = toExperimentStatus(event.status());
        Optional<Experiment> found = repository.findById(event.experimentId());
        if (found.isEmpty()) {
            return false;   // rule 1
        }
        Experiment experiment = found.get();
        Long currentRun = experiment.getActiveRunId();
        LocalDateTime when = parseTime(event.occurredAt());

        if (currentRun != null && event.runId() < currentRun) {
            return false;   // rule 2
        }
        boolean sameRun = event.runId().equals(currentRun);
        if (sameRun && experiment.getCurrentRunStatus() != null && experiment.getCurrentRunStatus().isTerminal()) {
            return false;   // rule 3
        }
        if (sameRun && experiment.getCurrentRunUpdatedAt() != null && when.isBefore(experiment.getCurrentRunUpdatedAt())) {
            return false;   // rule 4
        }

        experiment.setActiveRunId(event.runId());
        experiment.setCurrentRunStatus(newStatus);
        experiment.setCurrentRunStage(cut("QUEUED".equalsIgnoreCase(event.status()) ? "QUEUED" : event.stage(), MAX_STAGE));
        experiment.setCurrentRunMessage(cut(event.message(), MAX_MESSAGE));
        experiment.setCurrentRunUpdatedAt(when);
        log.info("Experiment {}: run {} is now {} ({})", experiment.getExperimentId(), event.runId(), newStatus,
                experiment.getCurrentRunStage());
        return true;   // saved at the end of the transaction (the entity is "managed": no save() needed)
    }

    static ExperimentRunStatus toExperimentStatus(String runStatus) {
        return switch (runStatus.trim().toUpperCase()) {
            case "QUEUED", "RUNNING" -> ExperimentRunStatus.RUNNING;
            case "COMPLETED" -> ExperimentRunStatus.COMPLETED;
            case "FAILED" -> ExperimentRunStatus.FAILED;
            case "CANCELLED" -> ExperimentRunStatus.TERMINATED;
            default -> throw new InvalidEventException("Unknown run status: " + runStatus);
        };
    }

    /** "2026-11-16T07:00:00Z" -> UTC date-time. Missing or broken -> now (the message is still useful). */
    static LocalDateTime parseTime(String text) {
        if (text != null && !text.isBlank()) {
            try {
                return OffsetDateTime.parse(text.trim()).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // fall through
            }
        }
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    static String cut(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}

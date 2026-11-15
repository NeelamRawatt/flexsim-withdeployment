package com.example.simulation_service.consumer;

import com.example.simulation_service.event.ExperimentDeletedEvent;
import com.example.simulation_service.event.KafkaTopics;
import com.example.simulation_service.service.SimulationRunCleanupService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The CONSUMER of "experiment.deleted" in simulation-service: delete ALL runs of that experiment.
 * (Same pattern as file-service.)
 *
 * Consumer group = "simulation-service" (application.properties): ONE group for all copies,
 * so each delete message is handled ONCE, by one copy. (The SSE listener is different on purpose:
 * there every copy needs every message.)
 *
 * IDEMPOTENT: the same message twice deletes 0 runs the second time. No harm.
 * Errors: broken message -> straight to experiment.deleted.DLT; other errors -> 3 retries, then DLT.
 */
@Component
public class ExperimentDeletedListener {

    private static final Logger log = LoggerFactory.getLogger(ExperimentDeletedListener.class);

    private final SimulationRunCleanupService cleanupService;
    private final JsonMapper json = JsonMapper.builder().build();

    public ExperimentDeletedListener(SimulationRunCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @KafkaListener(topics = KafkaTopics.EXPERIMENT_DELETED)
    public void onExperimentDeleted(ConsumerRecord<String, String> record) {
        ExperimentDeletedEvent event = parse(record.value());

        int deleted = cleanupService.deleteAllRunsOfExperiment(event.experimentId());

        log.info("experiment.deleted received (eventId {}, partition {}, offset {}): deleted {} run(s) of experiment {}",
                event.eventId(), record.partition(), record.offset(), deleted, event.experimentId());
    }

    ExperimentDeletedEvent parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidEventException("Empty experiment.deleted message");
        }
        ExperimentDeletedEvent event;
        try {
            event = json.readValue(value, ExperimentDeletedEvent.class);
        } catch (JacksonException e) {
            throw new InvalidEventException("experiment.deleted message is not valid JSON", e);
        }
        if (event.experimentId() == null) {
            throw new InvalidEventException("experiment.deleted message has no experimentId");
        }
        return event;
    }
}

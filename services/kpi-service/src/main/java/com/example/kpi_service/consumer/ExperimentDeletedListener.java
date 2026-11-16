package com.example.kpi_service.consumer;

import com.example.kpi_service.event.ExperimentDeletedEvent;
import com.example.kpi_service.event.KafkaTopics;
import com.example.kpi_service.service.KpiCleanupService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * "experiment.deleted" -> delete ALL KPIs of that experiment (same pattern as file- and simulation-service).
 * IDEMPOTENT: the same message twice deletes 0 rows the second time.
 */
@Component
public class ExperimentDeletedListener {

    private static final Logger log = LoggerFactory.getLogger(ExperimentDeletedListener.class);

    private final KpiCleanupService cleanupService;
    private final JsonMapper json = JsonMapper.builder().build();

    public ExperimentDeletedListener(KpiCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @KafkaListener(topics = KafkaTopics.EXPERIMENT_DELETED)
    public void onExperimentDeleted(ConsumerRecord<String, String> record) {
        ExperimentDeletedEvent event = parse(record.value());
        int deleted = cleanupService.deleteAllOfExperiment(event.experimentId());
        log.info("experiment.deleted received (eventId {}): deleted {} KPI snapshot(s) of experiment {}",
                event.eventId(), deleted, event.experimentId());
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

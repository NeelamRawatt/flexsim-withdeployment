package com.example.file_service.consumer;

import com.example.file_service.event.ExperimentDeletedEvent;
import com.example.file_service.event.KafkaTopics;
import com.example.file_service.service.UploadedFileService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The CONSUMER of "experiment.deleted".
 *
 * For every message: read the experimentId, delete ALL files of that experiment.
 *
 * Safe if the same message arrives twice (Kafka is "at least once"):
 * the second time there are no files left, so it deletes 0 files and nothing breaks.
 * This is called IDEMPOTENT.
 *
 * If something goes wrong:
 *   - broken message  -> InvalidEventException -> straight to experiment.deleted.DLT
 *   - other error (e.g. database busy) -> retried 3 times, 2 seconds apart, then DLT
 *   (rules in KafkaConsumerConfig)
 * Only when this method finishes WITHOUT an error does Kafka move our position (offset) forward.
 */
@Component
public class ExperimentDeletedListener {

    private static final Logger log = LoggerFactory.getLogger(ExperimentDeletedListener.class);

    private final UploadedFileService uploadedFileService;
    private final JsonMapper json = JsonMapper.builder().build();

    public ExperimentDeletedListener(UploadedFileService uploadedFileService) {
        this.uploadedFileService = uploadedFileService;
    }

    // groupId comes from application.properties (spring.kafka.consumer.group-id=file-service)
    @KafkaListener(topics = KafkaTopics.EXPERIMENT_DELETED)
    public void onExperimentDeleted(ConsumerRecord<String, String> record) {
        ExperimentDeletedEvent event = parse(record.value());

        int deleted = uploadedFileService.deleteAllForExperiment(event.experimentId());

        log.info("experiment.deleted received (eventId {}, partition {}, offset {}): deleted {} file(s) of experiment {}",
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
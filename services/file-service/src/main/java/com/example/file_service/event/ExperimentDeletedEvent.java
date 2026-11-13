package com.example.file_service.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The message file-service READS from topic "experiment.deleted".
 * Same fields as the record in experiment-service (each service keeps its own copy:
 * services share the message FORMAT, never Java classes).
 *
 * ignoreUnknown = true: if experiment-service adds a new field later, we don't break.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExperimentDeletedEvent(
        String eventId,
        Long experimentId,
        String deletedBy,
        String occurredAt) {
}
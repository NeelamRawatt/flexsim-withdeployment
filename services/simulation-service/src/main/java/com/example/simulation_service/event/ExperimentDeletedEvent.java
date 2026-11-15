package com.example.simulation_service.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The message simulation-service READS from topic "experiment.deleted".
 * Each service keeps its own copy of this record: services share the message FORMAT, never Java classes.
 * ignoreUnknown = true: if experiment-service adds a field later, we don't break.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExperimentDeletedEvent(
        String eventId,
        Long experimentId,
        String deletedBy,
        String occurredAt) {
}

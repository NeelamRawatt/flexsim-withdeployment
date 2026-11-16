package com.example.kpi_service.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The message on topic "experiment.deleted" (same as in the other services). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExperimentDeletedEvent(String eventId, Long experimentId, String deletedBy, String occurredAt) {
}

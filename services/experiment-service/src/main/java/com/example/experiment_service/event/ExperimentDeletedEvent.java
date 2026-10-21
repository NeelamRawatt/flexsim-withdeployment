package com.example.experiment_service.event;

/**
 * The message sent on topic "experiment.deleted".
 * Other services (simulation, kpi, file, ai-query) read it and delete THEIR data for this experiment.
 *
 * eventId     = a unique id for THIS message. If a reader gets the same message twice,
 *               it can see "I already handled this one" (Kafka may deliver twice).
 * experimentId = which experiment was deleted.
 * deletedBy    = username from the token (for audit logs).
 * occurredAt   = when, in UTC, as text like "2026-10-21T05:10:00Z".
 */
public record ExperimentDeletedEvent(
        String eventId,
        Long experimentId,
        String deletedBy,
        String occurredAt) {
}
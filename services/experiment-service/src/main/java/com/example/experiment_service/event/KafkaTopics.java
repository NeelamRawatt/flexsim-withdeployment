package com.example.experiment_service.event;

/** All topic names in ONE place, so a typo cannot happen in two places. */
public final class KafkaTopics {

    /** Made here (outbox): an experiment was deleted. */
    public static final String EXPERIMENT_DELETED = "experiment.deleted";

    /** Read here: a run's status changed (made by simulation-service). */
    public static final String SIMULATION_STATUS = "simulation.status";

    private KafkaTopics() {
    }
}

package com.example.simulation_service.event;

/** All topic names in ONE place, so a typo cannot happen in two places. */
public final class KafkaTopics {

    /** simulation-service SENDS this: "please run this simulation" (read by the flexsim-worker). */
    public static final String SIMULATION_TRIGGER_REQUESTED = "simulation.trigger.requested";

    /** simulation-service SENDS this: the run's status changed (QUEUED, RUNNING, COMPLETED, FAILED ...). */
    public static final String SIMULATION_STATUS = "simulation.status";

    /** simulation-service READS this: an experiment was deleted -> delete its runs. (Sent by experiment-service.) */
    public static final String EXPERIMENT_DELETED = "experiment.deleted";

    private KafkaTopics() {
    }
}

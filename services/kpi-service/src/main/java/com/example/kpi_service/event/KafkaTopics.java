package com.example.kpi_service.event;

/** Topic names, in ONE place. Same names as in the other services. */
public final class KafkaTopics {

    /** KPI snapshots from FlexSim (made by the flexsim-worker). */
    public static final String SIMULATION_KPI = "simulation.kpi";
    /** An experiment was deleted (made by experiment-service). */
    public static final String EXPERIMENT_DELETED = "experiment.deleted";

    private KafkaTopics() {
    }
}

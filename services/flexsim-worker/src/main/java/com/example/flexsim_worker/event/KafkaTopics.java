package com.example.flexsim_worker.event;

/** Topic names, in ONE place (a typo would silently listen to nothing). Same names as simulation-service. */
public final class KafkaTopics {

    /** The jobs: "please run this simulation". Made by simulation-service, read by the worker. */
    public static final String SIMULATION_TRIGGER_REQUESTED = "simulation.trigger.requested";

    private KafkaTopics() {
    }
}

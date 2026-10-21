package com.example.experiment_service.event;

/** All topic names in ONE place, so a typo cannot happen in two places. */
public final class KafkaTopics {

    public static final String EXPERIMENT_DELETED = "experiment.deleted";

    private KafkaTopics() {
    }
}
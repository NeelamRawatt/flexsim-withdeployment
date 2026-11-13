package com.example.file_service.event;

/** All topic names in ONE place, so a typo cannot happen in two places. */
public final class KafkaTopics {

    /** file-service SENDS this: a valid file was saved. */
    public static final String FILE_VALIDATED = "file.validated";

    /** file-service READS this: an experiment was deleted -> delete its files. (Sent by experiment-service.) */
    public static final String EXPERIMENT_DELETED = "experiment.deleted";

    private KafkaTopics() {
    }
}
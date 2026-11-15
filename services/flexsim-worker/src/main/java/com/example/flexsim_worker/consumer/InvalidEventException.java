package com.example.flexsim_worker.consumer;

/**
 * The job message itself is broken (not JSON, no runId, wrong times ...). Trying again can never fix it,
 * so the error handler sends it straight to "simulation.trigger.requested.DLT". See KafkaConsumerConfig.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}

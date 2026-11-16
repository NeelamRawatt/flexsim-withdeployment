package com.example.experiment_service.consumer;

/**
 * The message itself is broken (not JSON, no experimentId, unknown status ...). Trying again can never
 * fix it, so the error handler sends it straight to "simulation.status.DLT". See KafkaConsumerConfig.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}

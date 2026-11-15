package com.example.simulation_service.consumer;

/**
 * The message itself is broken (not JSON, or no experimentId). Trying again can never fix it,
 * so the error handler sends it straight to the "failed messages" topic (DLT). See KafkaConsumerConfig.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}

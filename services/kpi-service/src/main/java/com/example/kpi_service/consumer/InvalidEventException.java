package com.example.kpi_service.consumer;

/**
 * The message itself is broken (not JSON, no eventId / experimentId ...). Trying again can never fix it,
 * so the error handler sends it straight to the "<topic>.DLT" topic. See KafkaConsumerConfig.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}

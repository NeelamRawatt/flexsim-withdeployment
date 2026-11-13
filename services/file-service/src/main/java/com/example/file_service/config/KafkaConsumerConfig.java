package com.example.file_service.config;

import com.example.file_service.consumer.InvalidEventException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * What happens when a listener FAILS on a message.
 *
 * Without this, a message that always fails would be retried for ever and BLOCK
 * every message behind it in that partition.
 *
 * Our rules:
 *   1. Retry 3 more times, waiting 2 seconds each time (fixes short problems, e.g. database busy).
 *   2. Still failing -> put the message in "<topic>.DLT" (Dead Letter Topic = the "failed messages" box),
 *      same partition number, and continue with the next message.
 *   3. Broken message (InvalidEventException) -> NO retries (they can't help), straight to the DLT.
 *
 * Spring Boot gives this error handler to every @KafkaListener automatically.
 * The DLT topics must exist (we turned off automatic topic creation): see docker-compose.kafka.yml.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer sendToDeadLetterTopic = new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler handler = new DefaultErrorHandler(sendToDeadLetterTopic, new FixedBackOff(2000L, 3L));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
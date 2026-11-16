package com.example.experiment_service.config;

import com.example.experiment_service.consumer.InvalidEventException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * What happens when a listener FAILS on a message (same rules as the other services):
 *   1. retry 3 more times, 2 seconds apart
 *   2. still failing -> "simulation.status.DLT", then continue with the next message
 *   3. broken message (InvalidEventException) -> no retries, straight to the DLT
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

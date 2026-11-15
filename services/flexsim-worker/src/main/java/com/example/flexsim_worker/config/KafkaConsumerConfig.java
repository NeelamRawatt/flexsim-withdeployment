package com.example.flexsim_worker.config;

import com.example.flexsim_worker.consumer.InvalidEventException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * What happens when the job listener FAILS on a message:
 *
 *   broken message (InvalidEventException) -> no retries, straight to simulation.trigger.requested.DLT
 *   other error (for example simulation-service is down BEFORE FlexSim started)
 *                                          -> try again 6 times, waiting 2, 4, 8, 16, 32, 60 s (about 2 min),
 *                                             then the DLT.
 *
 * Why longer waits than in the other services: a short restart of simulation-service must not
 * throw away a user's run.
 *
 * IMPORTANT: once FlexSim has started, SimulationJobRunner never throws (it reports FAILED itself).
 * So a retry can never start FlexSim twice for the same run.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer sendToDeadLetterTopic = new DeadLetterPublishingRecoverer(kafkaTemplate);

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(6);
        backOff.setInitialInterval(2_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(60_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(sendToDeadLetterTopic, backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}

package com.example.experiment_service.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @EnableScheduling = switches ON Spring's "timer", so methods with @Scheduled run by themselves
 * (here: OutboxPublisher every 2 seconds). Without this class, @Scheduled does nothing.
 * Switched off together with the outbox sender (app.outbox.enabled=false).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
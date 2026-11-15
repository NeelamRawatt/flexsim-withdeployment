package com.example.flexsim_worker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Switches on @Scheduled methods (the nightly cleanup of old run folders). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

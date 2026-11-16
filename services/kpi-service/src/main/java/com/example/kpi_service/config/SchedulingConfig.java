package com.example.kpi_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Switches on @Scheduled methods (the 20-second heartbeat of the live stream). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

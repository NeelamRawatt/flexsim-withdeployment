package com.example.flexsim_worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The start point of flexsim-worker.
 *
 * What the worker does (all steps together):
 *   W2: logs in to auth-service as WORKER and can talk to simulation-service
 *   W3: receives jobs from Kafka, max 4 at a time, and runs the real FlexSim
 *   W4: a small API on localhost:8090, so FlexSim can read its data and report progress, KPIs and the end;
 *       safe shutdown, nightly cleanup of old run folders, Windows service files, CI
 *
 * @ConfigurationPropertiesScan = find classes like FlexsimSettings and fill them from application.properties.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FlexsimWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlexsimWorkerApplication.class, args);
    }
}

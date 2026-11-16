package com.example.kpi_service;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The start point of kpi-service (port 8084).
 *
 *   IN : Kafka topic simulation.kpi (made by the flexsim-worker)  -> saved in kpi_db
 *        Kafka topic experiment.deleted                         -> delete that experiment's KPIs
 *   OUT: GET /api/metrics/{exp}                      the whole KPI history of a run (React charts)
 *        GET /api/metrics/blockage-intervals/{exp}   when each chute was blocked
 *        GET /api/metrics/stream/{exp}               live KPIs while the run is going on (SSE)
 * Same URLs as the old app, so React keeps working.
 */
@SpringBootApplication
public class KpiServiceApplication {

    public static void main(String[] args) {
        // The PostgreSQL driver does not accept "Asia/Calcutta"; all our times are UTC anyway.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(KpiServiceApplication.class, args);
    }
}

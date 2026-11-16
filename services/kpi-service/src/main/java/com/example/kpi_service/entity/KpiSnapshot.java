package com.example.kpi_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * ONE saved KPI snapshot (table kpi_snapshot).
 * payload = the cleaned-up snapshot as JSON text; @JdbcTypeCode(JSON) tells Hibernate the column is JSONB.
 */
@Entity
@Table(name = "kpi_snapshot")
public class KpiSnapshot {

    public KpiSnapshot() {
    }

    public KpiSnapshot(String eventId, Long runId, Long experimentId, Double simulationTime,
                       LocalDateTime receivedAt, String payload) {
        this.eventId = eventId;
        this.runId = runId;
        this.experimentId = experimentId;
        this.simulationTime = simulationTime;
        this.receivedAt = receivedAt;
        this.payload = payload;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "run_id")
    private Long runId;

    @Column(name = "experiment_id", nullable = false)
    private Long experimentId;

    @Column(name = "simulation_time", nullable = false)
    private Double simulationTime;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getRunId() {
        return runId;
    }

    public Long getExperimentId() {
        return experimentId;
    }

    public Double getSimulationTime() {
        return simulationTime;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public String getPayload() {
        return payload;
    }
}

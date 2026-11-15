package com.example.simulation_service.entity;

import com.example.simulation_service.enums.RunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * ONE simulation run of one experiment (an experiment can be run many times; each time = one row).
 *
 * status  = where the run is (QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED)
 * stage   = the detail inside RUNNING, as FlexSim reports it (example: LOADING_PARCELS)
 * runSettings = the settings sent to the worker (start/end time, speed ...), as JSON text,
 *               so we can always see exactly what was run.
 *
 * @Version = "optimistic locking": a counter that goes up on every save. If two updates arrive
 * at the same time (example: two status messages), the second one sees the counter changed and
 * fails instead of silently overwriting the first one.
 */
@Entity
@Table(name = "simulation_run")
public class SimulationRun {

    public SimulationRun() {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The experiment (lives in experiment-service). Plain number, no foreign key (other database).
    @Column(nullable = false)
    private Long experimentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunStatus status;

    @Column(length = 50)
    private String stage;

    @Column(length = 500)
    private String message;

    @Column(columnDefinition = "TEXT")
    private String runSettings;

    // Who started it (from the token)
    @Column(nullable = false)
    private String requestedBy;

    private Long requestedByUserId;

    @Column(nullable = false)
    private LocalDateTime queuedAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void beforeFirstSave() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (queuedAt == null) {
            queuedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }

    public Long getExperimentId() { return experimentId; }
    public void setExperimentId(Long experimentId) { this.experimentId = experimentId; }

    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getRunSettings() { return runSettings; }
    public void setRunSettings(String runSettings) { this.runSettings = runSettings; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public Long getRequestedByUserId() { return requestedByUserId; }
    public void setRequestedByUserId(Long requestedByUserId) { this.requestedByUserId = requestedByUserId; }

    public LocalDateTime getQueuedAt() { return queuedAt; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public Long getVersion() { return version; }
}
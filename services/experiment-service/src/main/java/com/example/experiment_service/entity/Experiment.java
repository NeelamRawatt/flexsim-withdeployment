package com.example.experiment_service.entity;

import com.example.experiment_service.enums.ExperimentRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * One row of the table "experiment" = one simulation scenario.
 *
 * An "entity" is a Java class that matches ONE database table. One object = one row.
 * Hibernate (the JPA library) reads and writes the rows for us, so we write no SQL.
 *
 * WHAT CHANGED FROM THE OLD MONOLITH CLASS:
 *  1. No @ManyToOne User. We keep userId + ownerUsername (the user is in another database).
 *  2. No lists of children (infeedResources, zoneResources) and no simulationRuns.
 *     Children are loaded with their own repositories, and runs live in simulation-service.
 *  3. No Lombok and no Jackson annotations. Plain getters/setters. The JSON shape is the
 *     job of DTO classes (Step 5.6), so the database class never leaks to the outside.
 *  4. Status is PENDING for a new experiment (the old app wrongly showed FAILED).
 */
@Entity
@Table(name = "experiment")
public class Experiment {

    // JPA needs an empty constructor to build objects from database rows.
    public Experiment() {
    }

    // Primary key. The number comes from the database counter exp_sequence (1001, 1002, ...).
    // allocationSize = 1 means: ask the database for ONE number at a time. It must match the
    // sequence in the V1 file (INCREMENT BY 1), or Hibernate refuses to start.
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "exp_seq")
    @SequenceGenerator(name = "exp_seq", sequenceName = "exp_sequence", allocationSize = 1)
    private Long experimentId;

    private String experimentName;

    private String terminal;

    private String sortingType;

    @Column(nullable = false)
    private Integer useCaseId;

    // A date without time, like 2026-10-18.
    private LocalDate selectedDate;

    // Shift start and end, like 23:00:00. No time zone on purpose (see the V1 file).
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer parcelCount;

    private Integer newParcelCount;

    private Integer parcelChangeValue;

    // increase or decrease
    private String parcelChangeMode;

    private Integer maxRecirculationCount;

    private Integer initialRunSpeed;

    // 0 or 1
    private Integer flexibleResources;

    // 0 or 1 (always 0 when flexibleResources is 0)
    private Integer phoneBoomChute;

    // 0 or 1
    private Integer dropChuteFlexible;

    // 0 or 1
    private Integer flexibleUnloadResources;

    // 0 to 100, default 90
    private Integer sorterFullPercent;

    // 50 to 90, default 65
    private Integer minimumCageOccupancy;

    // 50 to 90, default 75, never below the minimum
    private Integer maximumCageOccupancy;

    @Column(length = 30)
    private String guiMode;

    @Column(length = 20)
    private String allSortedAt;

    // The owner. Only a number: the user lives in auth_db (no foreign key across databases).
    private Long userId;

    // A copy of the owner's name, so we can show it without calling auth-service.
    private String ownerUsername;

    // Filled automatically in UTC.
    private LocalDateTime createdAt;

    // Latest run info (a small copy; the real run records live in simulation-service).
    // EnumType.STRING = save the word, not a number.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExperimentRunStatus currentRunStatus = ExperimentRunStatus.PENDING;

    // Plain text here. The list of stages belongs to simulation-service.
    @Column(length = 50)
    private String currentRunStage;

    @Column(length = 300)
    private String currentRunMessage;

    private LocalDateTime currentRunUpdatedAt;

    private Long activeRunId;


    /**
     * Runs just before the FIRST save. Gives safe defaults (same rules as the old app)
     * and stamps the creation time in UTC.
     */
    @PrePersist
    void beforeFirstSave() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
        if (currentRunStatus == null) {
            currentRunStatus = ExperimentRunStatus.PENDING;
        }
        applyDefaults();
    }

    /** Runs just before every UPDATE. */
    @PreUpdate
    void beforeUpdate() {
        applyDefaults();
    }

    private void applyDefaults() {
        if (flexibleResources == null) {
            flexibleResources = 1;
        }
        if (flexibleResources == 0) {
            phoneBoomChute = 0;           // no flexible resources means no phone boom chute
        } else if (phoneBoomChute == null) {
            phoneBoomChute = 1;
        }
        if (flexibleUnloadResources == null) {
            flexibleUnloadResources = 0;
        }
        if (sorterFullPercent == null) {
            sorterFullPercent = 90;
        }
    }

    public Long getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(Long experimentId) {
        this.experimentId = experimentId;
    }

    public String getExperimentName() {
        return experimentName;
    }

    public void setExperimentName(String experimentName) {
        this.experimentName = experimentName;
    }

    public String getTerminal() {
        return terminal;
    }

    public void setTerminal(String terminal) {
        this.terminal = terminal;
    }

    public String getSortingType() {
        return sortingType;
    }

    public void setSortingType(String sortingType) {
        this.sortingType = sortingType;
    }

    public Integer getUseCaseId() {
        return useCaseId;
    }

    public void setUseCaseId(Integer useCaseId) {
        this.useCaseId = useCaseId;
    }

    public LocalDate getSelectedDate() {
        return selectedDate;
    }

    public void setSelectedDate(LocalDate selectedDate) {
        this.selectedDate = selectedDate;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getParcelCount() {
        return parcelCount;
    }

    public void setParcelCount(Integer parcelCount) {
        this.parcelCount = parcelCount;
    }

    public Integer getNewParcelCount() {
        return newParcelCount;
    }

    public void setNewParcelCount(Integer newParcelCount) {
        this.newParcelCount = newParcelCount;
    }

    public Integer getParcelChangeValue() {
        return parcelChangeValue;
    }

    public void setParcelChangeValue(Integer parcelChangeValue) {
        this.parcelChangeValue = parcelChangeValue;
    }

    public String getParcelChangeMode() {
        return parcelChangeMode;
    }

    public void setParcelChangeMode(String parcelChangeMode) {
        this.parcelChangeMode = parcelChangeMode;
    }

    public Integer getMaxRecirculationCount() {
        return maxRecirculationCount;
    }

    public void setMaxRecirculationCount(Integer maxRecirculationCount) {
        this.maxRecirculationCount = maxRecirculationCount;
    }

    public Integer getInitialRunSpeed() {
        return initialRunSpeed;
    }

    public void setInitialRunSpeed(Integer initialRunSpeed) {
        this.initialRunSpeed = initialRunSpeed;
    }

    public Integer getFlexibleResources() {
        return flexibleResources;
    }

    public void setFlexibleResources(Integer flexibleResources) {
        this.flexibleResources = flexibleResources;
    }

    public Integer getPhoneBoomChute() {
        return phoneBoomChute;
    }

    public void setPhoneBoomChute(Integer phoneBoomChute) {
        this.phoneBoomChute = phoneBoomChute;
    }

    public Integer getDropChuteFlexible() {
        return dropChuteFlexible;
    }

    public void setDropChuteFlexible(Integer dropChuteFlexible) {
        this.dropChuteFlexible = dropChuteFlexible;
    }

    public Integer getFlexibleUnloadResources() {
        return flexibleUnloadResources;
    }

    public void setFlexibleUnloadResources(Integer flexibleUnloadResources) {
        this.flexibleUnloadResources = flexibleUnloadResources;
    }

    public Integer getSorterFullPercent() {
        return sorterFullPercent;
    }

    public void setSorterFullPercent(Integer sorterFullPercent) {
        this.sorterFullPercent = sorterFullPercent;
    }

    public Integer getMinimumCageOccupancy() {
        return minimumCageOccupancy;
    }

    public void setMinimumCageOccupancy(Integer minimumCageOccupancy) {
        this.minimumCageOccupancy = minimumCageOccupancy;
    }

    public Integer getMaximumCageOccupancy() {
        return maximumCageOccupancy;
    }

    public void setMaximumCageOccupancy(Integer maximumCageOccupancy) {
        this.maximumCageOccupancy = maximumCageOccupancy;
    }

    public String getGuiMode() {
        return guiMode;
    }

    public void setGuiMode(String guiMode) {
        this.guiMode = guiMode;
    }

    public String getAllSortedAt() {
        return allSortedAt;
    }

    public void setAllSortedAt(String allSortedAt) {
        this.allSortedAt = allSortedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ExperimentRunStatus getCurrentRunStatus() {
        return currentRunStatus;
    }

    public void setCurrentRunStatus(ExperimentRunStatus currentRunStatus) {
        this.currentRunStatus = currentRunStatus;
    }

    public String getCurrentRunStage() {
        return currentRunStage;
    }

    public void setCurrentRunStage(String currentRunStage) {
        this.currentRunStage = currentRunStage;
    }

    public String getCurrentRunMessage() {
        return currentRunMessage;
    }

    public void setCurrentRunMessage(String currentRunMessage) {
        this.currentRunMessage = currentRunMessage;
    }

    public LocalDateTime getCurrentRunUpdatedAt() {
        return currentRunUpdatedAt;
    }

    public void setCurrentRunUpdatedAt(LocalDateTime currentRunUpdatedAt) {
        this.currentRunUpdatedAt = currentRunUpdatedAt;
    }

    public Long getActiveRunId() {
        return activeRunId;
    }

    public void setActiveRunId(Long activeRunId) {
        this.activeRunId = activeRunId;
    }
}
package com.example.experiment_service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Resources and chutes of one zone, for one shift. Many rows belong to one experiment.
 */
@Entity
@Table(name = "zone_resource_detail")
public class ZoneResourceDetail {

    public ZoneResourceDetail() {
    }

    // IDENTITY = the database makes the next number itself.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer shiftId;

    private String zoneId;

    private Integer resourceId;

    private String chutes;

    // Many rows point to ONE experiment. LAZY = the experiment is loaded only if we ask for it.
    // exp_id is the foreign key (same database, so it is allowed).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exp_id", nullable = false)
    private Experiment experiment;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getShiftId() {
        return shiftId;
    }

    public void setShiftId(Integer shiftId) {
        this.shiftId = shiftId;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public Integer getResourceId() {
        return resourceId;
    }

    public void setResourceId(Integer resourceId) {
        this.resourceId = resourceId;
    }

    public String getChutes() {
        return chutes;
    }

    public void setChutes(String chutes) {
        this.chutes = chutes;
    }

    public Experiment getExperiment() {
        return experiment;
    }

    public void setExperiment(Experiment experiment) {
        this.experiment = experiment;
    }
}
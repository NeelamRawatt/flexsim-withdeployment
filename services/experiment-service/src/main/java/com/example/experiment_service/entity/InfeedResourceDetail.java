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
 * Resources at one infeed, for one shift. Many rows belong to one experiment.
 */
@Entity
@Table(name = "infeed_resource_detail")
public class InfeedResourceDetail {

    public InfeedResourceDetail() {
    }

    // IDENTITY = the database makes the next number itself.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer shiftId;

    private String tc;

    private String infeed;

    private String zoneId;

    private Integer noOfResources;

    private Boolean active;

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

    public String getTc() {
        return tc;
    }

    public void setTc(String tc) {
        this.tc = tc;
    }

    public String getInfeed() {
        return infeed;
    }

    public void setInfeed(String infeed) {
        this.infeed = infeed;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public Integer getNoOfResources() {
        return noOfResources;
    }

    public void setNoOfResources(Integer noOfResources) {
        this.noOfResources = noOfResources;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Experiment getExperiment() {
        return experiment;
    }

    public void setExperiment(Experiment experiment) {
        this.experiment = experiment;
    }
}
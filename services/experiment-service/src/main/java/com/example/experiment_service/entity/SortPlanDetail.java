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
 * One line of the sort plan: old sort name -> new sort name.
 */
@Entity
@Table(name = "sort_plan_detail")
public class SortPlanDetail {

    public SortPlanDetail() {
    }

    // IDENTITY = the database makes the next number itself.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String oldSortName;

    private String newSortName;

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

    public String getOldSortName() {
        return oldSortName;
    }

    public void setOldSortName(String oldSortName) {
        this.oldSortName = oldSortName;
    }

    public String getNewSortName() {
        return newSortName;
    }

    public void setNewSortName(String newSortName) {
        this.newSortName = newSortName;
    }

    public Experiment getExperiment() {
        return experiment;
    }

    public void setExperiment(Experiment experiment) {
        this.experiment = experiment;
    }
}
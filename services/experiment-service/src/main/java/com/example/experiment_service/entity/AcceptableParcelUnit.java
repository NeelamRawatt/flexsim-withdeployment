package com.example.experiment_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * The allowed parcel size and weight limits of one experiment.
 * The id column has a special name in the table: acceptable_parcel_unit_id.
 */
@Entity
@Table(name = "acceptable_parcel_unit")
public class AcceptableParcelUnit {

    public AcceptableParcelUnit() {
    }

    // IDENTITY = the database makes the next number itself.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "acceptable_parcel_unit_id")
    private Long id;

    private Double minHeight;

    private Double maxHeight;

    private Double minLength;

    private Double maxLength;

    private Double minWidth;

    private Double maxWidth;

    private Double minWeight;

    private Double maxWeight;

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

    public Double getMinHeight() {
        return minHeight;
    }

    public void setMinHeight(Double minHeight) {
        this.minHeight = minHeight;
    }

    public Double getMaxHeight() {
        return maxHeight;
    }

    public void setMaxHeight(Double maxHeight) {
        this.maxHeight = maxHeight;
    }

    public Double getMinLength() {
        return minLength;
    }

    public void setMinLength(Double minLength) {
        this.minLength = minLength;
    }

    public Double getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Double maxLength) {
        this.maxLength = maxLength;
    }

    public Double getMinWidth() {
        return minWidth;
    }

    public void setMinWidth(Double minWidth) {
        this.minWidth = minWidth;
    }

    public Double getMaxWidth() {
        return maxWidth;
    }

    public void setMaxWidth(Double maxWidth) {
        this.maxWidth = maxWidth;
    }

    public Double getMinWeight() {
        return minWeight;
    }

    public void setMinWeight(Double minWeight) {
        this.minWeight = minWeight;
    }

    public Double getMaxWeight() {
        return maxWeight;
    }

    public void setMaxWeight(Double maxWeight) {
        this.maxWeight = maxWeight;
    }

    public Experiment getExperiment() {
        return experiment;
    }

    public void setExperiment(Experiment experiment) {
        this.experiment = experiment;
    }
}
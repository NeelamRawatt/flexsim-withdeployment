package com.example.experiment_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/**
 * One REAL parcel of a past shift (table parcel_details, filled only by the import script).
 *
 * @Immutable = the application only READS these rows. Hibernate never writes them,
 * so a bug can never change the real data.
 *
 * Only the columns the application uses are here. (The table has a few more, e.g. postal_code;
 * Hibernate "validate" only checks the columns that are mapped.)
 */
@Entity
@Immutable
@Table(name = "parcel_details")
public class ParcelDetail {

    public ParcelDetail() {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parcel_id")
    private String parcelId;

    @Column(name = "parcel_time_slot")
    private LocalDateTime parcelTimeSlot;

    @Column(name = "parcel_edt_date")
    private LocalDate parcelEdtDate;

    @Column(name = "parcel_infeed_name")
    private String parcelInfeedName;

    @Column(name = "sort_name")
    private String sortName;

    @Column(name = "reject_code")
    private String rejectCode;

    @Column(name = "parcel_weight")
    private Double parcelWeight;

    @Column(name = "parcel_length")
    private Double parcelLength;

    @Column(name = "parcel_width")
    private Double parcelWidth;

    @Column(name = "parcel_height")
    private Double parcelHeight;

    @Column(name = "parcel_volume")
    private Double parcelVolume;

    public Long getId() {
        return id;
    }

    public String getParcelId() {
        return parcelId;
    }

    public LocalDateTime getParcelTimeSlot() {
        return parcelTimeSlot;
    }

    public LocalDate getParcelEdtDate() {
        return parcelEdtDate;
    }

    public String getParcelInfeedName() {
        return parcelInfeedName;
    }

    public String getSortName() {
        return sortName;
    }

    public String getRejectCode() {
        return rejectCode;
    }

    public Double getParcelWeight() {
        return parcelWeight;
    }

    public Double getParcelLength() {
        return parcelLength;
    }

    public Double getParcelWidth() {
        return parcelWidth;
    }

    public Double getParcelHeight() {
        return parcelHeight;
    }

    public Double getParcelVolume() {
        return parcelVolume;
    }

    // ---- setters: only for tests (the application never changes a parcel) ----

    public void setParcelId(String parcelId) {
        this.parcelId = parcelId;
    }

    public void setParcelTimeSlot(LocalDateTime parcelTimeSlot) {
        this.parcelTimeSlot = parcelTimeSlot;
    }

    public void setParcelEdtDate(LocalDate parcelEdtDate) {
        this.parcelEdtDate = parcelEdtDate;
    }

    public void setParcelInfeedName(String parcelInfeedName) {
        this.parcelInfeedName = parcelInfeedName;
    }

    public void setSortName(String sortName) {
        this.sortName = sortName;
    }

    public void setParcelWeight(Double parcelWeight) {
        this.parcelWeight = parcelWeight;
    }
}

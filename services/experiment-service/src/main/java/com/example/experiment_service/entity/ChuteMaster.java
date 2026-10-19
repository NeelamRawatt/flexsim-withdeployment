package com.example.experiment_service.entity;

import com.example.experiment_service.enums.ChuteType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One chute of the terminal. Reference data (a fixed list), not linked to an experiment. */
@Entity
@Table(name = "chute_master")
public class ChuteMaster {

    public ChuteMaster() {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer chuteId;

    @Column(nullable = false)
    private Integer zoneId;

    @Column(nullable = false)
    private String zoneName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChuteType chuteType;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getChuteId() {
        return chuteId;
    }

    public void setChuteId(Integer chuteId) {
        this.chuteId = chuteId;
    }

    public Integer getZoneId() {
        return zoneId;
    }

    public void setZoneId(Integer zoneId) {
        this.zoneId = zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public ChuteType getChuteType() {
        return chuteType;
    }

    public void setChuteType(ChuteType chuteType) {
        this.chuteType = chuteType;
    }
}
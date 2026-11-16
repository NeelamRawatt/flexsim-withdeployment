package com.example.kpi_service.kpi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The sorter belt: occupied slots, and occupancy = occupied slots / 522 (the belt has 522 slots). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConveyorDetails(Integer occupiedSlots, Double occupancy) {
}

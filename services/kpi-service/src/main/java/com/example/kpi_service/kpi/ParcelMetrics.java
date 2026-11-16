package com.example.kpi_service.kpi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Parcel totals of one snapshot (same fields as the old app's ParcelMetricsDTO). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParcelMetrics(
        Long experimentId,
        Integer parcelScanned,
        Integer parcelThroughput,
        Integer parcelBlockage,
        Integer parcelReject,
        Integer parcelRejectRefeed,
        Double simulationTime) {
}

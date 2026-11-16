package com.example.kpi_service.kpi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** What one operator (resource) handled so far (same fields as the old app's ResourceMetricsDTO). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResourceMetrics(Integer zoneId, Integer shiftId, Integer parcelsHandled, Integer weightHandled) {
}

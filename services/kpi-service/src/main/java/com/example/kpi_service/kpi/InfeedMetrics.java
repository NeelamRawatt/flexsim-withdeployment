package com.example.kpi_service.kpi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** How many parcels one infeed put on the sorter so far. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InfeedMetrics(Integer induction) {
}

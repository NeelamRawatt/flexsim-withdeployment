package com.example.experiment_service.dto;

import java.util.Map;

/**
 * Answer of POST /api/parcels/getSortPlan:
 *   {"totalParcelCount": 3, "sortNameCounts": {"10661": 2, "10440": 1}}
 */
public record SortPlanResponseDto(Long totalParcelCount, Map<String, Long> sortNameCounts) {
}

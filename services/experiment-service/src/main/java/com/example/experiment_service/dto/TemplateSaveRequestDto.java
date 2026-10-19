package com.example.experiment_service.dto;

import java.util.List;
import java.util.Map;

/**
 * What the client SENDS to save a template.
 * There is NO username field on purpose: the owner always comes from the login token.
 * If an old client still sends "username", it is simply ignored.
 *
 * type: "infeed", "zone" or "sort_plan" (also accepts "sortPlan" and "SORT-PLAN").
 * data: the Excel rows. One row = one map like {"shiftId": "1", "tc": "TCA", "resourceCount": 3}.
 */
public record TemplateSaveRequestDto(
        String type,
        String templateName,
        String fileName,
        List<Map<String, Object>> data) {
}
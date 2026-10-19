package com.example.experiment_service.dto;

import java.util.List;

/** The answer of GET /api/templates: the templates grouped by type, newest first. */
public record TemplateListResponseDto(
        List<TemplateItemDto> infeed,
        List<TemplateItemDto> zone,
        List<TemplateItemDto> sortPlan) {
}
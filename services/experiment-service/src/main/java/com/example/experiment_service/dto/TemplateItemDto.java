package com.example.experiment_service.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** One saved template as the client sees it. type is lower case: "infeed", "zone" or "sort_plan". */
public record TemplateItemDto(
        Long id,
        String name,
        String type,
        String fileName,
        Integer rowCount,
        LocalDateTime createdAt,
        List<Map<String, Object>> data) {
}
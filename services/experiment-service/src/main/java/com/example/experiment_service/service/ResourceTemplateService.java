package com.example.experiment_service.service;

import com.example.experiment_service.dto.TemplateItemDto;
import com.example.experiment_service.dto.TemplateListResponseDto;
import com.example.experiment_service.dto.TemplateSaveRequestDto;
import com.example.experiment_service.entity.ResourceTemplate;
import com.example.experiment_service.entity.ResourceTemplateRow;
import com.example.experiment_service.enums.TemplateType;
import com.example.experiment_service.repository.ResourceTemplateRepository;
import com.example.experiment_service.repository.ResourceTemplateRowRepository;
import com.example.experiment_service.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Saved templates: an Excel table (infeed, zone or sort plan) that a user saved to load again later.
 *
 * Rules (same as the old app unless written below):
 *  - Everybody who is logged in can LIST all templates (the controller allows it).
 *  - Only ADMIN and DEVELOPER can SAVE and DELETE (the controller checks the role).
 *  - A template can be deleted only by the person who saved it.
 *
 * What is new:
 *  - The owner comes from the login token, never from the request body.
 *  - Bad input gives a clean 400 (the old app gave 500).
 *  - Size limits, so one request cannot fill the database.
 *  - The list reads all rows with ONE query (no N+1 problem).
 *  - Delete is one SQL statement; the database removes the rows (ON DELETE CASCADE).
 */
@Service
public class ResourceTemplateService {

    private static final int MAX_ROWS = 5000;
    private static final int MAX_COLUMNS_PER_ROW = 50;
    private static final int MAX_KEY_LENGTH = 100;
    private static final int MAX_CELL_TEXT_LENGTH = 1000;
    private static final int MAX_NAME_LENGTH = 255;

    private final ResourceTemplateRepository templateRepository;
    private final ResourceTemplateRowRepository rowRepository;

    public ResourceTemplateService(
            ResourceTemplateRepository templateRepository, ResourceTemplateRowRepository rowRepository) {
        this.templateRepository = templateRepository;
        this.rowRepository = rowRepository;
    }

    // ------------------------------------------------------------------ SAVE

    @Transactional
    public TemplateItemDto save(TemplateSaveRequestDto request, AuthenticatedUser user) {
        if (request == null) {
            throw bad("Request body is required");
        }
        TemplateType type = parseType(request.type());
        String templateName = trimToNull(request.templateName());
        String fileName = trimToNull(request.fileName());
        checkNameLength(templateName, "templateName");
        checkNameLength(fileName, "fileName");

        List<Map<String, Object>> data = request.data() == null ? List.of() : request.data();
        if (data.isEmpty()) {
            throw bad("Template data cannot be empty");
        }
        if (data.size() > MAX_ROWS) {
            throw bad("Too many rows (max " + MAX_ROWS + ")");
        }
        for (int i = 0; i < data.size(); i++) {
            validateRow(type, data.get(i), i + 1);
        }

        ResourceTemplate template = new ResourceTemplate();
        template.setTemplateType(type);
        template.setTemplateName(templateName == null ? defaultName(type) : templateName);
        template.setFileName(fileName == null ? "template-data" : fileName);
        template.setRowCount(data.size());
        template.setPayloadJson("[]");
        template.setUserId(user.userId());
        template.setOwnerUsername(user.username());

        for (int i = 0; i < data.size(); i++) {
            ResourceTemplateRow row = new ResourceTemplateRow();
            row.setRowIndex(i);
            row.setRowData(data.get(i));
            template.addRow(row);          // sets both sides of the link
        }

        // cascade = ALL on the entity: saving the template also saves its rows.
        ResourceTemplate saved = templateRepository.save(template);
        return toDto(saved, data);
    }

    // ------------------------------------------------------------------ LIST

    @Transactional(readOnly = true)
    public TemplateListResponseDto list() {
        List<ResourceTemplate> infeed = templateRepository.findByTemplateTypeOrderByCreatedAtDesc(TemplateType.INFEED);
        List<ResourceTemplate> zone = templateRepository.findByTemplateTypeOrderByCreatedAtDesc(TemplateType.ZONE);
        List<ResourceTemplate> sortPlan = templateRepository.findByTemplateTypeOrderByCreatedAtDesc(TemplateType.SORT_PLAN);

        List<Long> ids = new ArrayList<>();
        infeed.forEach(t -> ids.add(t.getId()));
        zone.forEach(t -> ids.add(t.getId()));
        sortPlan.forEach(t -> ids.add(t.getId()));

        // ONE query for the rows of ALL templates, then we sort them into groups here.
        Map<Long, List<Map<String, Object>>> rowsByTemplate = new HashMap<>();
        if (!ids.isEmpty()) {
            for (ResourceTemplateRow row : rowRepository.findByTemplate_IdInOrderByTemplate_IdAscRowIndexAsc(ids)) {
                if (row.getRowData() != null) {
                    rowsByTemplate
                            .computeIfAbsent(row.getTemplate().getId(), key -> new ArrayList<>())
                            .add(row.getRowData());
                }
            }
        }

        return new TemplateListResponseDto(
                toDtos(infeed, rowsByTemplate),
                toDtos(zone, rowsByTemplate),
                toDtos(sortPlan, rowsByTemplate));
    }

    // ---------------------------------------------------------------- DELETE

    /** Deletes a template of this user. Not found OR not yours both give 404 (we do not tell which). */
    @Transactional
    public void delete(Long templateId, AuthenticatedUser user) {
        templateRepository.findByIdAndUserId(templateId, user.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));
        templateRepository.deleteTemplateById(templateId);
    }

    // --------------------------------------------------------------- helpers

    private List<TemplateItemDto> toDtos(List<ResourceTemplate> templates, Map<Long, List<Map<String, Object>>> rowsByTemplate) {
        return templates.stream()
                .map(t -> toDto(t, rowsByTemplate.getOrDefault(t.getId(), List.of())))
                .toList();
    }

    private TemplateItemDto toDto(ResourceTemplate t, List<Map<String, Object>> data) {
        return new TemplateItemDto(
                t.getId(),
                t.getTemplateName(),
                t.getTemplateType().name().toLowerCase(Locale.ROOT),
                t.getFileName(),
                t.getRowCount(),
                t.getCreatedAt(),
                data);
    }

    private TemplateType parseType(String value) {
        String text = trimToNull(value);
        if (text == null) {
            throw bad("Template type is required");
        }
        String normalized = text.toUpperCase(Locale.ROOT).replace("-", "_");
        if ("SORTPLAN".equals(normalized)) {
            normalized = "SORT_PLAN";
        }
        try {
            return TemplateType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw bad("Unsupported template type: " + text);
        }
    }

    private String defaultName(TemplateType type) {
        return switch (type) {
            case INFEED -> "Infeed Template";
            case ZONE -> "Zone Template";
            case SORT_PLAN -> "Sort Plan Template";
        };
    }

    private void validateRow(TemplateType type, Map<String, Object> row, int rowNumber) {
        if (row == null || row.isEmpty()) {
            throw bad("Row " + rowNumber + " is empty");
        }
        if (row.size() > MAX_COLUMNS_PER_ROW) {
            throw bad("Row " + rowNumber + " has too many columns (max " + MAX_COLUMNS_PER_ROW + ")");
        }
        for (Map.Entry<String, Object> cell : row.entrySet()) {
            if (cell.getKey() == null || cell.getKey().length() > MAX_KEY_LENGTH) {
                throw bad("Row " + rowNumber + " has a column name that is too long");
            }
            if (cell.getValue() instanceof String text && text.length() > MAX_CELL_TEXT_LENGTH) {
                throw bad("Row " + rowNumber + " has a value that is too long (max " + MAX_CELL_TEXT_LENGTH + ")");
            }
        }
        switch (type) {
            case INFEED -> validateInfeedRow(row, rowNumber);
            case ZONE -> validateZoneRow(row, rowNumber);
            case SORT_PLAN -> validateSortPlanRow(row, rowNumber);
        }
    }

    private void validateInfeedRow(Map<String, Object> row, int rowNumber) {
        if (cellText(row, "shiftId") == null || cellText(row, "tc") == null) {
            throw bad("Invalid infeed row at " + rowNumber + " (shiftId and tc are required)");
        }
        int count;
        try {
            count = new BigDecimal(String.valueOf(row.get("resourceCount")).trim()).intValueExact();
        } catch (RuntimeException e) {
            throw bad("Invalid infeed resourceCount at " + rowNumber);
        }
        if (count < 0) {
            throw bad("Infeed resourceCount cannot be negative at " + rowNumber);
        }
    }

    private void validateZoneRow(Map<String, Object> row, int rowNumber) {
        if (cellText(row, "shiftId") == null || cellText(row, "zoneId") == null || cellText(row, "chuteIdRaw") == null) {
            throw bad("Invalid zone row at " + rowNumber + " (shiftId, zoneId and chuteIdRaw are required)");
        }
    }

    private void validateSortPlanRow(Map<String, Object> row, int rowNumber) {
        if (cellText(row, "oldSortName") == null || cellText(row, "newSortName") == null) {
            throw bad("Invalid sort plan row at " + rowNumber + " (oldSortName and newSortName are required)");
        }
    }

    /** The value of a cell as trimmed text, or null when it is missing, null or only spaces. */
    private static String cellText(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : trimToNull(String.valueOf(value));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void checkNameLength(String value, String field) {
        if (value != null && value.length() > MAX_NAME_LENGTH) {
            throw bad(field + " is too long (max " + MAX_NAME_LENGTH + " characters)");
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
package com.example.file_service.validation;

import com.example.file_service.dto.FileValidationResult;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Checks a ZONE_RESOURCE Excel file. Rules taken from your team's file-service
 * (same zones, same chute ranges), with production fixes (see the list at the bottom).
 *
 * The file (first sheet) must have these 4 columns, in THIS order, in row 1:
 *   Shift ID | Zone ID | Resource | Chute_ID
 *
 * Rules per row:
 *   - Shift ID: whole number 1 to 8
 *   - Zone ID: ZONE-1, ZONE-2, ZONE-3 or ZONE-4
 *   - Resource: whole number, 0 or more
 *   - Chute_ID: chute numbers separated by commas, with or without [ ]   example: [101,102,103] or 101
 *   - every chute must belong to that zone (see ZONE_RULES)
 *   - all chutes in ONE row must be of the SAME chute type (example: all Spiral)
 * Rule for the whole file:
 *   - EVERY shift 1..8 must have EVERY zone (8 x 4 = 32 combinations)
 *
 * Fixes compared to the team's code:
 *   - Shift 9 or 0 is now refused with a clear message
 *   - empty rows are skipped instead of giving "Row N is empty"
 *   - Java error texts are no longer sent to the user (they go to the log)
 */
@Component
public class ZoneResourceFileValidator {

    private static final Logger log = LoggerFactory.getLogger(ZoneResourceFileValidator.class);

    static final List<String> EXPECTED_HEADERS = List.of("Shift ID", "Zone ID", "Resource", "Chute_ID");

    static final Set<String> EXPECTED_ZONES = Set.of("ZONE-1", "ZONE-2", "ZONE-3", "ZONE-4");

    /** Which chute numbers belong to which zone, and their type (copied from the team's code). */
    private static final Map<String, List<ChuteRange>> ZONE_RULES = Map.of(
            "ZONE-1", List.of(new ChuteRange(252, 252, "Reject Chute")),
            "ZONE-2", List.of(
                    new ChuteRange(101, 114, "Spiral Chute"),
                    new ChuteRange(121, 135, "Boom Conveyor Chute"),
                    new ChuteRange(301, 337, "Direct Chute")),
            "ZONE-3", List.of(new ChuteRange(152, 152, "Reject Chute")),
            "ZONE-4", List.of(
                    new ChuteRange(201, 214, "Spiral Chute"),
                    new ChuteRange(221, 236, "Boom Conveyor Chute"),
                    new ChuteRange(402, 433, "Direct Chute")));

    private static final int SHIFT_COLUMN = 0;
    private static final int ZONE_COLUMN = 1;
    private static final int RESOURCE_COLUMN = 2;
    private static final int CHUTE_COLUMN = 3;

    public FileValidationResult validate(byte[] content) {
        List<String> errors = new ArrayList<>();
        Map<Integer, Set<String>> zonesPerShift = new TreeMap<>();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0 || workbook.getSheetAt(0).getPhysicalNumberOfRows() == 0) {
                return FileValidationResult.failed("Zone resource file validation failed", List.of("The file contains no data"));
            }
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() > ExcelRows.MAX_DATA_ROWS) {
                return FileValidationResult.failed("Zone resource file validation failed",
                        List.of("Too many rows. The maximum is " + ExcelRows.MAX_DATA_ROWS + "."));
            }
            DataFormatter formatter = new DataFormatter();

            // ---- 1. the 4 headers, in fixed order ----
            Row header = sheet.getRow(0);
            if (ExcelRows.isBlank(header, formatter)) {
                return FileValidationResult.failed("Zone resource file validation failed", List.of("The header row (row 1) is missing"));
            }
            for (int column = 0; column < EXPECTED_HEADERS.size(); column++) {
                String actual = ExcelRows.text(header, column, formatter);
                if (!EXPECTED_HEADERS.get(column).equalsIgnoreCase(actual)) {
                    errors.add("Invalid column at position " + (column + 1) + ". Expected '"
                            + EXPECTED_HEADERS.get(column) + "' but found '" + actual + "'");
                }
            }

            // ---- 2. every data row (stop at the FIRST mistake of a row, like the team's code) ----
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (ExcelRows.isBlank(row, formatter)) {
                    continue;
                }
                int excelRow = i + 1;
                String shiftText = ExcelRows.text(row, SHIFT_COLUMN, formatter);
                String zone = ExcelRows.text(row, ZONE_COLUMN, formatter);
                String resource = ExcelRows.text(row, RESOURCE_COLUMN, formatter);
                String chutes = ExcelRows.text(row, CHUTE_COLUMN, formatter);

                if (shiftText.isEmpty() || zone.isEmpty() || resource.isEmpty() || chutes.isEmpty()) {
                    errors.add("Shift ID, Zone ID, Resource and Chute_ID are mandatory at row " + excelRow);
                    continue;
                }
                Integer shift = ExcelRows.parseShift(shiftText, "Shift ID", excelRow, errors);
                if (shift == null) {
                    continue;
                }
                if (!EXPECTED_ZONES.contains(zone)) {
                    errors.add("Invalid Zone ID '" + zone + "' at row " + excelRow);
                    continue;
                }
                if (!resource.matches("\\d+")) {
                    errors.add("Invalid Resource '" + resource + "' at row " + excelRow
                            + " (must be a whole number, 0 or more)");
                    continue;
                }
                if (!chutes.matches("^\\[?\\s*\\d+(\\s*,\\s*\\d+)*\\s*]?$")) {
                    errors.add("Invalid Chute_ID format at row " + excelRow
                            + ". Chute ids must be comma separated like [101,102,103]");
                    continue;
                }

                List<Integer> chuteIds = parseChuteIds(chutes);
                List<Integer> wrongChutes = new ArrayList<>();
                Set<String> typesInRow = new HashSet<>();
                for (Integer chuteId : chuteIds) {
                    String type = chuteType(zone, chuteId);
                    if (type == null) {
                        wrongChutes.add(chuteId);
                    } else {
                        typesInRow.add(type);
                    }
                }
                if (!wrongChutes.isEmpty()) {
                    errors.add("Invalid chute ids " + wrongChutes + " for " + zone + " at row " + excelRow);
                    continue;
                }
                if (typesInRow.size() > 1) {
                    errors.add("All chute ids in row " + excelRow + " must belong to the same chute type");
                    continue;
                }
                zonesPerShift.computeIfAbsent(shift, key -> new TreeSet<>()).add(zone);
            }

            // ---- 3. every shift must have every zone ----
            Set<Integer> missingShifts = new TreeSet<>();
            for (int shift = ExcelRows.FIRST_SHIFT; shift <= ExcelRows.LAST_SHIFT; shift++) {
                if (!zonesPerShift.containsKey(shift)) {
                    missingShifts.add(shift);
                }
            }
            if (!missingShifts.isEmpty()) {
                errors.add("Missing Shift IDs: " + missingShifts);
            }
            for (Map.Entry<Integer, Set<String>> entry : zonesPerShift.entrySet()) {
                Set<String> missingZones = new TreeSet<>(EXPECTED_ZONES);
                missingZones.removeAll(entry.getValue());
                if (!missingZones.isEmpty()) {
                    errors.add("Shift " + entry.getKey() + " is missing zones: " + missingZones);
                }
            }
        } catch (Exception e) {
            log.warn("Zone resource file could not be read: {}", e.toString());
            return FileValidationResult.failed("Zone resource file validation failed", List.of(ExcelRows.UNREADABLE));
        }

        return errors.isEmpty()
                ? FileValidationResult.ok("Zone resource file is valid")
                : FileValidationResult.failed("Zone resource file validation failed", ExcelRows.limit(errors));
    }

    /** "[101, 102]" -> [101, 102]. The format was already checked, so every part is a number. */
    private static List<Integer> parseChuteIds(String value) {
        List<Integer> ids = new ArrayList<>();
        for (String part : value.replace("[", "").replace("]", "").split(",")) {
            if (!part.isBlank()) {
                ids.add(Integer.parseInt(part.trim()));
            }
        }
        return ids;
    }

    /** The chute type for this zone and chute number, or null if the chute is not in that zone. */
    private static String chuteType(String zone, int chuteId) {
        for (ChuteRange range : ZONE_RULES.getOrDefault(zone, List.of())) {
            if (chuteId >= range.first() && chuteId <= range.last()) {
                return range.type();
            }
        }
        return null;
    }

    /** One block of chute numbers, example 101..114 = "Spiral Chute". */
    private record ChuteRange(int first, int last, String type) {
    }
}
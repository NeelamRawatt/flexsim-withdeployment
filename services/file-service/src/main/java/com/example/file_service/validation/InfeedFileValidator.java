package com.example.file_service.validation;

import com.example.file_service.dto.FileValidationResult;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Checks an INFEED Excel file. Rules taken from your team's file-service (same TC list, same shifts),
 * with some production fixes (see the list at the bottom).
 *
 * The file (first sheet) must have the columns (any order, any upper/lower case):
 *   Shift_ID | TC | No of Resources
 *
 * Rules:
 *   - Shift_ID: a whole number from 1 to 8
 *   - TC: one of the 8 known TC values (see EXPECTED_TCS)
 *   - No of Resources: a whole number, 0 or more
 *   - EVERY shift 1..8 must have EVERY TC (8 x 8 = 64 combinations)
 *
 * Fixes compared to the team's code:
 *   - "No of Resources" is now really checked (before: the column was found but never checked)
 *   - Shift 9 or 0 is now refused (before: accepted without a message)
 *   - "missing TC" for a shift is reported ONCE (before: the same line 8 times, a loop bug)
 *   - empty rows are skipped instead of giving mistakes
 *   - Java error texts are no longer sent to the user (they are written to the log instead)
 */
@Component
public class InfeedFileValidator {

    private static final Logger log = LoggerFactory.getLogger(InfeedFileValidator.class);

    static final Set<String> EXPECTED_TCS = Set.of(
            "KIP1-KIP2", "KIP3-KIP4", "KIP5-KIP6", "KIP7-KIP8",
            "KIP9-KIP10", "B9-B10-B11", "B14-B15", "B113-B116");

    public FileValidationResult validate(byte[] content) {
        List<String> errors = new ArrayList<>();
        // shift -> the TC values found for it (TreeMap/TreeSet = sorted, so messages are always in the same order)
        Map<Integer, Set<String>> tcsPerShift = new TreeMap<>();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0 || workbook.getSheetAt(0).getPhysicalNumberOfRows() == 0) {
                return FileValidationResult.failed("Infeed file validation failed", List.of("The file contains no data"));
            }
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() > ExcelRows.MAX_DATA_ROWS) {
                return FileValidationResult.failed("Infeed file validation failed",
                        List.of("Too many rows. The maximum is " + ExcelRows.MAX_DATA_ROWS + "."));
            }
            DataFormatter formatter = new DataFormatter();

            // ---- 1. find the 3 columns by their header names ----
            Row header = sheet.getRow(0);
            if (ExcelRows.isBlank(header, formatter)) {
                return FileValidationResult.failed("Infeed file validation failed", List.of("The header row (row 1) is missing"));
            }
            int shiftColumn = -1;
            int tcColumn = -1;
            int resourcesColumn = -1;
            for (Cell cell : header) {
                String name = formatter.formatCellValue(cell).trim();
                if ("Shift_ID".equalsIgnoreCase(name)) {
                    shiftColumn = cell.getColumnIndex();
                } else if ("TC".equalsIgnoreCase(name)) {
                    tcColumn = cell.getColumnIndex();
                } else if ("No of Resources".equalsIgnoreCase(name)) {
                    resourcesColumn = cell.getColumnIndex();
                }
            }
            if (shiftColumn < 0 || tcColumn < 0 || resourcesColumn < 0) {
                return FileValidationResult.failed("Infeed file validation failed",
                        List.of("Required columns missing: 'Shift_ID', 'TC' and 'No of Resources' must be present in row 1"));
            }

            // ---- 2. check every data row ----
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (ExcelRows.isBlank(row, formatter)) {
                    continue;
                }
                int excelRow = i + 1;   // row number as the user sees it in Excel

                Integer shift = ExcelRows.parseShift(
                        ExcelRows.text(row, shiftColumn, formatter), "Shift_ID", excelRow, errors);

                String tc = ExcelRows.text(row, tcColumn, formatter);
                boolean tcIsValid = EXPECTED_TCS.contains(tc);
                if (tc.isEmpty()) {
                    errors.add("TC is missing at row " + excelRow);
                } else if (!tcIsValid) {
                    errors.add("Invalid TC '" + tc + "' at row " + excelRow);
                }

                String resources = ExcelRows.text(row, resourcesColumn, formatter);
                if (resources.isEmpty()) {
                    errors.add("No of Resources is missing at row " + excelRow);
                } else if (!resources.matches("\\d+")) {
                    errors.add("Invalid No of Resources '" + resources + "' at row " + excelRow
                            + " (must be a whole number, 0 or more)");
                }

                if (shift != null && tcIsValid) {
                    tcsPerShift.computeIfAbsent(shift, key -> new TreeSet<>()).add(tc);
                }
            }

            // ---- 3. every shift must have every TC ----
            Set<Integer> missingShifts = new TreeSet<>();
            for (int shift = ExcelRows.FIRST_SHIFT; shift <= ExcelRows.LAST_SHIFT; shift++) {
                if (!tcsPerShift.containsKey(shift)) {
                    missingShifts.add(shift);
                }
            }
            if (!missingShifts.isEmpty()) {
                errors.add("Missing Shift IDs: " + missingShifts);
            }
            for (Map.Entry<Integer, Set<String>> entry : tcsPerShift.entrySet()) {
                Set<String> missingTcs = new TreeSet<>(EXPECTED_TCS);
                missingTcs.removeAll(entry.getValue());
                if (!missingTcs.isEmpty()) {
                    errors.add("Shift ID " + entry.getKey() + " is missing TC values: " + missingTcs);
                }
            }
        } catch (Exception e) {
            // The details go to OUR log, not to the user.
            log.warn("Infeed file could not be read: {}", e.toString());
            return FileValidationResult.failed("Infeed file validation failed", List.of(ExcelRows.UNREADABLE));
        }

        return errors.isEmpty()
                ? FileValidationResult.ok("Infeed file is valid")
                : FileValidationResult.failed("Infeed file validation failed", ExcelRows.limit(errors));
    }
}
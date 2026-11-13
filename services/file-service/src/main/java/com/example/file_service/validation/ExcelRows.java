package com.example.file_service.validation;

import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;

/** Small helpers shared by the two Excel checkers. */
final class ExcelRows {

    /** Shown to the user when the file cannot be opened. No Java details (they help attackers). */
    static final String UNREADABLE = "The file could not be read. Please upload a valid Excel file (.xlsx or .xls).";

    /** Protection: a file with more data rows than this is refused (stops "huge file" attacks). */
    static final int MAX_DATA_ROWS = 10_000;

    /** At most this many mistakes are sent back; then "... and N more". Keeps the answer small. */
    static final int MAX_ERRORS = 100;

    /** Allowed shift numbers: 1 to 8. */
    static final int FIRST_SHIFT = 1;
    static final int LAST_SHIFT = 8;

    private ExcelRows() {
    }

    /** true if the row does not exist or every cell is empty. Empty rows are simply skipped. */
    static boolean isBlank(Row row, DataFormatter formatter) {
        if (row == null) {
            return true;
        }
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** The text of one cell, exactly as Excel shows it, without spaces at the ends. "" if empty. */
    static String text(Row row, int column, DataFormatter formatter) {
        if (column < 0) {
            return "";
        }
        return formatter.formatCellValue(row.getCell(column)).trim();
    }

    /**
     * Reads a shift number. Adds a clear mistake to errors and returns null when it is
     * missing, not a number, or not between 1 and 8.
     */
    static Integer parseShift(String value, String columnName, int excelRow, List<String> errors) {
        if (value.isEmpty()) {
            errors.add(columnName + " is missing at row " + excelRow);
            return null;
        }
        int shift;
        try {
            shift = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            errors.add("Invalid " + columnName + " '" + value + "' at row " + excelRow);
            return null;
        }
        if (shift < FIRST_SHIFT || shift > LAST_SHIFT) {
            errors.add(columnName + " " + shift + " at row " + excelRow
                    + " is not allowed (allowed: " + FIRST_SHIFT + " to " + LAST_SHIFT + ")");
            return null;
        }
        return shift;
    }

    /** Keeps at most MAX_ERRORS mistakes, and says how many more there were. */
    static List<String> limit(List<String> errors) {
        if (errors.size() <= MAX_ERRORS) {
            return errors;
        }
        List<String> shortList = new ArrayList<>(errors.subList(0, MAX_ERRORS));
        shortList.add("... and " + (errors.size() - MAX_ERRORS) + " more mistakes");
        return shortList;
    }
}
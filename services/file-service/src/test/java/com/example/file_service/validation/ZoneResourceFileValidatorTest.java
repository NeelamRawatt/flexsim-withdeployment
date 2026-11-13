package com.example.file_service.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.file_service.dto.FileValidationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZoneResourceFileValidatorTest {

    private static final String[] HEADER = {"Shift ID", "Zone ID", "Resource", "Chute_ID"};

    // One correct chute value per zone
    private static final Map<String, String> GOOD_CHUTES = Map.of(
            "ZONE-1", "252",
            "ZONE-2", "[101,102,103]",
            "ZONE-3", "152",
            "ZONE-4", "[221, 222]");

    private final ZoneResourceFileValidator validator = new ZoneResourceFileValidator();

    /** A complete, correct file: every shift 1..8 with every zone = 32 data rows. */
    private static List<String[]> completeRows() {
        List<String[]> rows = new ArrayList<>();
        rows.add(HEADER);
        for (int shift = 1; shift <= 8; shift++) {
            for (String zone : List.of("ZONE-1", "ZONE-2", "ZONE-3", "ZONE-4")) {
                rows.add(new String[] {String.valueOf(shift), zone, "1", GOOD_CHUTES.get(zone)});
            }
        }
        return rows;
    }

    @Test
    void aCompleteFileIsValid() {
        FileValidationResult result = validator.validate(TestWorkbooks.xlsx(completeRows()));

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void wrongHeaderIsReported() {
        List<String[]> rows = completeRows();
        rows.set(0, new String[] {"Shift ID", "Zone", "Resource", "Chute_ID"});

        assertThat(validator.validate(TestWorkbooks.xlsx(rows)).errors())
                .contains("Invalid column at position 2. Expected 'Zone ID' but found 'Zone'");
    }

    @Test
    void rowMistakesAreReportedWithTheExcelRowNumber() {
        List<String[]> rows = completeRows();
        rows.set(1, new String[] {"1", "ZONE-9", "1", "252"});            // unknown zone          (row 2)
        rows.set(2, new String[] {"1", "ZONE-2", "x", "101"});            // resource not a number (row 3)
        rows.set(3, new String[] {"1", "ZONE-3", "1", "1;2"});            // bad chute format      (row 4)
        rows.set(4, new String[] {"1", "ZONE-4", "1", "[101]"});          // chute of ZONE-2       (row 5)
        rows.set(5, new String[] {"2", "ZONE-1", "", "252"});             // missing value         (row 6)

        List<String> errors = validator.validate(TestWorkbooks.xlsx(rows)).errors();

        assertThat(errors).contains(
                "Invalid Zone ID 'ZONE-9' at row 2",
                "Invalid Resource 'x' at row 3 (must be a whole number, 0 or more)",
                "Invalid Chute_ID format at row 4. Chute ids must be comma separated like [101,102,103]",
                "Invalid chute ids [101] for ZONE-4 at row 5",
                "Shift ID, Zone ID, Resource and Chute_ID are mandatory at row 6");
    }

    @Test
    void chutesOfDifferentTypesInOneRowAreRefused() {
        List<String[]> rows = completeRows();
        rows.set(2, new String[] {"1", "ZONE-2", "1", "[101,121]"});   // Spiral + Boom Conveyor

        assertThat(validator.validate(TestWorkbooks.xlsx(rows)).errors())
                .contains("All chute ids in row 3 must belong to the same chute type");
    }

    @Test
    void shiftOutsideOneToEightIsRefused() {
        List<String[]> rows = completeRows();
        rows.add(new String[] {"9", "ZONE-1", "1", "252"});

        assertThat(validator.validate(TestWorkbooks.xlsx(rows)).errors())
                .contains("Shift ID 9 at row 34 is not allowed (allowed: 1 to 8)");
    }

    @Test
    void missingZonesAndShiftsAreReported() {
        List<String[]> rows = completeRows();
        rows.removeIf(r -> "8".equals(r[0]));
        rows.removeIf(r -> "5".equals(r[0]) && "ZONE-3".equals(r[1]));

        assertThat(validator.validate(TestWorkbooks.xlsx(rows)).errors())
                .containsExactly("Missing Shift IDs: [8]", "Shift 5 is missing zones: [ZONE-3]");
    }

    @Test
    void emptyRowsAreSkipped() {
        List<String[]> rows = completeRows();
        rows.add(3, null);
        assertThat(validator.validate(TestWorkbooks.xlsx(rows)).valid()).isTrue();
    }
}
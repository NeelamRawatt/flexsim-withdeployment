package com.example.file_service.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.file_service.dto.FileValidationResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class InfeedFileValidatorTest {

    private static final String[] HEADER = {"Shift_ID", "TC", "No of Resources"};

    private final InfeedFileValidator validator = new InfeedFileValidator();

    /** A complete, correct file: every shift 1..8 with every TC = 64 data rows. */
    private static List<String[]> completeRows() {
        List<String[]> rows = new ArrayList<>();
        rows.add(HEADER);
        for (int shift = 1; shift <= 8; shift++) {
            for (String tc : new TreeSet<>(InfeedFileValidator.EXPECTED_TCS)) {
                rows.add(new String[] {String.valueOf(shift), tc, "2"});
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
    void headersMayBeInAnyOrderAndAnyCase() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"no of resources", "tc", "SHIFT_ID"});
        for (String[] r : completeRows().subList(1, 65)) {
            rows.add(new String[] {r[2], r[1], r[0]});
        }
        assertThat(validator.validate(TestWorkbooks.xlsx(rows)).valid()).isTrue();
    }

    @Test
    void emptyRowsAreSkipped() {
        List<String[]> rows = completeRows();
        rows.add(5, null);   // an empty row in the middle
        assertThat(validator.validate(TestWorkbooks.xlsx(rows)).valid()).isTrue();
    }

    @Test
    void missingColumnGivesOneClearMistake() {
        byte[] file = TestWorkbooks.xlsx(List.of(new String[] {"Shift_ID", "TC"}, new String[] {"1", "KIP1-KIP2"}));

        FileValidationResult result = validator.validate(file);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).singleElement().asString().contains("'No of Resources'");
    }

    @Test
    void wrongValuesAreReportedWithTheExcelRowNumber() {
        List<String[]> rows = completeRows();
        rows.set(1, new String[] {"9", "KIP1-KIP2", "2"});      // shift 9 is not allowed  (Excel row 2)
        rows.set(2, new String[] {"1", "KIP99", "2"});          // unknown TC              (Excel row 3)
        rows.set(3, new String[] {"1", "KIP5-KIP6", "-1"});     // negative resources      (Excel row 4)
        rows.set(4, new String[] {"abc", "KIP7-KIP8", "2"});    // shift is not a number   (Excel row 5)

        FileValidationResult result = validator.validate(TestWorkbooks.xlsx(rows));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains(
                "Shift_ID 9 at row 2 is not allowed (allowed: 1 to 8)",
                "Invalid TC 'KIP99' at row 3",
                "Invalid No of Resources '-1' at row 4 (must be a whole number, 0 or more)",
                "Invalid Shift_ID 'abc' at row 5");
    }

    @Test
    void aMissingShiftIsReported() {
        List<String[]> rows = completeRows();
        rows.removeIf(r -> "8".equals(r[0]));   // remove all rows of shift 8

        FileValidationResult result = validator.validate(TestWorkbooks.xlsx(rows));

        assertThat(result.errors()).containsExactly("Missing Shift IDs: [8]");
    }

    @Test
    void aMissingTcIsReportedOnceForItsShift() {
        List<String[]> rows = completeRows();
        rows.removeIf(r -> "3".equals(r[0]) && "B14-B15".equals(r[1]));

        FileValidationResult result = validator.validate(TestWorkbooks.xlsx(rows));

        // exactly ONE line (the team's code wrote this line 8 times)
        assertThat(result.errors()).containsExactly("Shift ID 3 is missing TC values: [B14-B15]");
    }

    @Test
    void aBrokenFileGivesAFriendlyMessageWithoutJavaDetails() {
        FileValidationResult result = validator.validate("not excel".getBytes(StandardCharsets.UTF_8));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly(ExcelRows.UNREADABLE);
    }
}
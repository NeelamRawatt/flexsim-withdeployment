package com.example.file_service.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExcelFileGuardTest {

    private final ExcelFileGuard guard = new ExcelFileGuard();

    private static byte[] realXlsx() {
        return TestWorkbooks.xlsx(List.<String[]>of(new String[] {"A"}));
    }

    @Test
    void aRealXlsxFilePasses() {
        assertThat(guard.check("Infeed.XLSX", realXlsx())).isEmpty();
    }

    @Test
    void emptyFileIsRefused() {
        assertThat(guard.check("a.xlsx", new byte[0])).contains("The file is empty. Please upload an Excel file.");
        assertThat(guard.check("a.xlsx", null)).isPresent();
    }

    @Test
    void otherFileTypesAreRefused() {
        assertThat(guard.check("virus.exe", realXlsx())).contains("Only Excel files (.xlsx or .xls) are allowed.");
        assertThat(guard.check("noextension", realXlsx())).isPresent();
    }

    @Test
    void aRenamedNonExcelFileIsRefused() {
        byte[] text = "this is not excel".getBytes(StandardCharsets.UTF_8);
        assertThat(guard.check("data.xlsx", text)).contains("The file content is not a real Excel file.");
        // an .xlsx file renamed to .xls has the wrong signature too
        assertThat(guard.check("data.xls", realXlsx())).contains("The file content is not a real Excel file.");
    }

    @Test
    void tooBigFileIsRefused() {
        byte[] big = new byte[(int) ExcelFileGuard.MAX_BYTES + 1];
        assertThat(guard.check("a.xlsx", big)).contains("The file is too big. The maximum is 10 MB.");
    }

    @Test
    void extensionIsReadInLowerCase() {
        assertThat(ExcelFileGuard.extensionOf("Report.Final.XLSX")).isEqualTo("xlsx");
        assertThat(ExcelFileGuard.extensionOf(null)).isEmpty();
    }
}
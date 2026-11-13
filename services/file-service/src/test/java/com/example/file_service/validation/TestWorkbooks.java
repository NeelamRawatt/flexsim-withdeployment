package com.example.file_service.validation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Builds small REAL .xlsx files in memory for the tests (no files on disk needed). */
final class TestWorkbooks {

    private TestWorkbooks() {
    }

    /** Each String[] is one row; the first one is the header. null = leave the row completely empty. */
    static byte[] xlsx(List<String[]> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            for (int r = 0; r < rows.size(); r++) {
                String[] values = rows.get(r);
                if (values == null) {
                    continue;
                }
                Row row = sheet.createRow(r);
                for (int c = 0; c < values.length; c++) {
                    row.createCell(c).setCellValue(values[c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
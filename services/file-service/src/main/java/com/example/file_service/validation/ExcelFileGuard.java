package com.example.file_service.validation;

import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The FIRST check of every upload, before we even open the file:
 *  1. not empty
 *  2. not bigger than 10 MB
 *  3. the name ends with .xlsx or .xls
 *  4. the CONTENT really is an Excel file
 *
 * Why 4: the name alone can lie. Someone can rename "virus.exe" to "data.xlsx".
 * Every file type starts with fixed "magic bytes" (a signature):
 *   .xlsx = a ZIP file      -> starts with 50 4B 03 04  ("PK..")
 *   .xls  = an old Office file -> starts with D0 CF 11 E0 A1 B1 1A E1
 */
@Component
public class ExcelFileGuard {

    static final long MAX_BYTES = 10L * 1024 * 1024;

    private static final byte[] XLSX_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] XLS_SIGNATURE = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    /** Returns the mistake as text, or empty if the file passed all 4 checks. */
    public Optional<String> check(String fileName, byte[] content) {
        if (content == null || content.length == 0) {
            return Optional.of("The file is empty. Please upload an Excel file.");
        }
        if (content.length > MAX_BYTES) {
            return Optional.of("The file is too big. The maximum is 10 MB.");
        }
        String extension = extensionOf(fileName);
        if (!"xlsx".equals(extension) && !"xls".equals(extension)) {
            return Optional.of("Only Excel files (.xlsx or .xls) are allowed.");
        }
        byte[] signature = "xlsx".equals(extension) ? XLSX_SIGNATURE : XLS_SIGNATURE;
        if (!startsWith(content, signature)) {
            return Optional.of("The file content is not a real Excel file.");
        }
        return Optional.empty();
    }

    /** "Infeed.XLSX" -> "xlsx". "" when there is no dot. */
    public static String extensionOf(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
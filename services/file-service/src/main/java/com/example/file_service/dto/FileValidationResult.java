package com.example.file_service.dto;

import java.util.List;

/**
 * The answer of a file check, sent back to React.
 *   valid   = true if the file has NO mistakes
 *   message = one short sentence
 *   errors  = every mistake found, with the Excel row number (empty when valid)
 *
 * Example: {"valid":false,"message":"Infeed file validation failed",
 *           "errors":["Invalid TC 'KIP99' at row 4","Missing Shift IDs: [8]"]}
 */
public record FileValidationResult(boolean valid, String message, List<String> errors) {

    public static FileValidationResult ok(String message) {
        return new FileValidationResult(true, message, List.of());
    }

    public static FileValidationResult failed(String message, List<String> errors) {
        return new FileValidationResult(false, message, List.copyOf(errors));
    }
}
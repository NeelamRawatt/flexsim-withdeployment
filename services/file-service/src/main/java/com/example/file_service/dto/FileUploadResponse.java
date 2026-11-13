package com.example.file_service.dto;

import java.util.List;

/**
 * The answer to an upload. Same first 3 fields as your team's file-service (valid, message, errors),
 * plus 2 new ones:
 *   file       = the details of the saved file (null when the file was refused)
 *   filesReady = true when this experiment now has BOTH files (INFEED and ZONE_RESOURCE)
 *
 * Accepted -> HTTP 201.   Refused -> HTTP 400 with every mistake in "errors".
 */
public record FileUploadResponse(
        boolean valid,
        String message,
        List<String> errors,
        FileDetailsDto file,
        boolean filesReady) {

    public static FileUploadResponse accepted(String message, FileDetailsDto file, boolean filesReady) {
        return new FileUploadResponse(true, message, List.of(), file, filesReady);
    }

    public static FileUploadResponse rejected(String message, List<String> errors) {
        return new FileUploadResponse(false, message, List.copyOf(errors), null, false);
    }
}
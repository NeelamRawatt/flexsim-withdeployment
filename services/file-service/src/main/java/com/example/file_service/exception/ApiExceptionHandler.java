package com.example.file_service.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

/**
 * One place that turns errors into clean JSON answers (same idea as experiment-service).
 * We handle only KNOWN errors. No catch-all on purpose: unknown errors stay a plain 500
 * with no details, and they show up loudly in our logs.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Our own errors (404 not found, 403 not owner, 409 conflict, 503 ...) keep their status and our text. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        String message = ex.getReason() == null ? "" : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", message));
    }

    /** The file is bigger than the limit in application.properties -> 413 "Payload Too Large". */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleTooBig(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(Map.of("message", "The file is too big. The maximum is 10 MB."));
    }

    /** A form field is missing (example: no experimentId) -> 400. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingField(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", "Missing field: " + ex.getParameterName()));
    }

    /** The file itself is missing in the upload -> 400. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, String>> handleMissingFile(MissingServletRequestPartException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", "Missing file: " + ex.getRequestPartName()));
    }

    /** A value has the wrong type (example: experimentId = "abc") -> 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleWrongType(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid value for: " + ex.getName()));
    }
}
package com.example.experiment_service.exception;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * One place that turns errors into clean JSON answers.
 * "@RestControllerAdvice" = this class watches ALL controllers.
 *
 * We handle ONLY three kinds of error. We do NOT catch every Exception on purpose:
 * a catch-all would also catch security errors (403) and turn them into 500.
 * Unknown errors stay 500 with no details (server.error.include-message=never).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** A rule in the DTO failed (for example a text that is too long) -> 400. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError error = ex.getBindingResult().getFieldError();
        String field = error == null ? "request" : error.getField();
        String message = error == null ? "Invalid request" : error.getDefaultMessage();
        return ResponseEntity.badRequest().body(Map.of("field", field, "message", String.valueOf(message)));
    }

    /** The body is missing or is not valid JSON -> 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", "Request body is missing or is not valid JSON"));
    }

    /** Our own errors (404 not found, 403 not owner, 400 bad time ...) keep their status and our text. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        String message = ex.getReason() == null ? "" : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", message));
    }
}
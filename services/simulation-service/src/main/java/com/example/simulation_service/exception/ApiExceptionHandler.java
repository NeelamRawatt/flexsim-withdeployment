package com.example.simulation_service.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * One place that turns KNOWN errors into clean JSON answers (same idea as the other services).
 * No catch-all on purpose: unknown errors stay a plain 500 and show up loudly in the logs.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Our own errors (404, 403, 409, 503 ...) keep their status and our text. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", ex.getReason() == null ? "" : ex.getReason()));
    }

    /** Two updates of the same run at the same moment (@Version clash) -> 409: the caller should retry. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleClash(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "The run was changed at the same moment by another update. Please retry."));
    }

    /** A rule on the request body failed (example: experimentId missing) -> 400 with the field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleInvalid(MethodArgumentNotValidException ex) {
        FieldError error = ex.getBindingResult().getFieldError();
        String field = error == null ? "" : error.getField();
        return ResponseEntity.badRequest().body(Map.of("field", field, "message", field + " is missing or invalid"));
    }

    /** The body is missing or is not valid JSON -> 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", "Request body is missing or is not valid JSON"));
    }

    /** A value in the URL has the wrong type (example: /runs/abc) -> 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleWrongType(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid value for: " + ex.getName()));
    }
}
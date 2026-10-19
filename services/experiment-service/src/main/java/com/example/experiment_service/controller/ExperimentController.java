package com.example.experiment_service.controller;

import com.example.experiment_service.dto.ExperimentDto;
import com.example.experiment_service.dto.PageResponse;
import com.example.experiment_service.security.AuthenticatedUser;
import com.example.experiment_service.service.ExperimentService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The URLs for experiments. Same URLs as the old app, so the React app needs no change.
 * A controller is a waiter: it takes the order, gives it to the service, and brings the answer.
 *
 * @PreAuthorize on the class = every method needs one of these roles (same as the old rules:
 * OPERATOR, ADMIN, DEVELOPER; a VIEWER gets 403). Not logged in gives 401 (from SecurityConfig).
 *
 * @AuthenticationPrincipal AuthenticatedUser = Spring gives us the logged-in user that
 * JwtAuthenticationFilter put in the request. It is read from the token, not from the database.
 *
 * @Valid = check the rules written in ExperimentDto (@Size ...) before the method starts.
 */
@RestController
@RequestMapping("/api/experiments")
@PreAuthorize("hasAnyRole('OPERATOR','ADMIN','DEVELOPER')")
public class ExperimentController {

    private final ExperimentService experimentService;

    public ExperimentController(ExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    /** GET /api/experiments/history?page=0&size=8&sort=createdAt,desc&status=COMPLETED */
    @GetMapping("/history")
    public PageResponse<ExperimentDto> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String status) {
        return experimentService.history(page, size, sort, status);
    }

    /** POST /api/experiments/createExperiment */
    @PostMapping("/createExperiment")
    public ResponseEntity<ExperimentDto> createExperiment(
            @Valid @RequestBody ExperimentDto dto,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(experimentService.create(user, dto));
    }

    /** GET /api/experiments/{experimentId} */
    @GetMapping("/{experimentId}")
    public ResponseEntity<ExperimentDto> getExperimentById(@PathVariable Long experimentId) {
        return ResponseEntity.ok(experimentService.getById(experimentId));
    }

    /** PUT /api/experiments/{experimentId}  (only the owner) */
    @PutMapping("/{experimentId}")
    public ResponseEntity<ExperimentDto> updateExperiment(
            @PathVariable Long experimentId,
            @Valid @RequestBody ExperimentDto dto,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(experimentService.update(experimentId, dto, user));
    }

    /** PUT /api/experiments/{experimentId}/all-sorted-at  with body {"allsortedat":"06:15:00"} (only the owner) */
    @PutMapping("/{experimentId}/all-sorted-at")
    public ResponseEntity<ExperimentDto> updateAllSortedAt(
            @PathVariable Long experimentId,
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal AuthenticatedUser user) {
        // The old callers used two spellings, so we accept both.
        Object raw = payload.get("allsortedat");
        if (raw == null) {
            raw = payload.get("allSortedAt");
        }
        String allSortedAt = raw == null ? null : String.valueOf(raw);
        return ResponseEntity.ok(experimentService.updateAllSortedAt(experimentId, allSortedAt, user));
    }

    /** DELETE /api/experiments/{experimentId}  (owner, or ADMIN / DEVELOPER) */
    @DeleteMapping("/{experimentId}")
    public ResponseEntity<String> deleteExperiment(
            @PathVariable Long experimentId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        experimentService.delete(experimentId, user);
        return ResponseEntity.ok("Experiment deleted successfully");
    }
}
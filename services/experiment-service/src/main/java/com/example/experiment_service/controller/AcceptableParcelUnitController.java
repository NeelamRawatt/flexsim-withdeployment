package com.example.experiment_service.controller;

import com.example.experiment_service.dto.AcceptableParcelUnitDto;
import com.example.experiment_service.security.AuthenticatedUser;
import com.example.experiment_service.service.AcceptableParcelUnitService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The URLs for the allowed parcel size and weight limits. Same URLs as the old app.
 * (In the old app the GET URL was open to everybody. Now it needs a login.)
 */
@RestController
@RequestMapping("/api/acceptable-parcel-unit")
@PreAuthorize("hasAnyRole('OPERATOR','ADMIN','DEVELOPER')")
public class AcceptableParcelUnitController {

    private final AcceptableParcelUnitService service;

    public AcceptableParcelUnitController(AcceptableParcelUnitService service) {
        this.service = service;
    }

    @PostMapping("/save")
    public ResponseEntity<String> save(@RequestBody AcceptableParcelUnitDto dto, @AuthenticationPrincipal AuthenticatedUser user) {
        service.save(dto, user);
        return ResponseEntity.ok("Acceptable Parcel Unit Saved successfully");
    }

    /** 200 with the limits, or 204 (No Content) when nothing was saved yet. */
    @GetMapping("/byExperiment/{experimentId}")
    public ResponseEntity<AcceptableParcelUnitDto> getByExperiment(@PathVariable Long experimentId) {
        return service.getByExperimentId(experimentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
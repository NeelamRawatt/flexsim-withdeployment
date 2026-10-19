package com.example.experiment_service.controller;

import com.example.experiment_service.dto.FlexsimInfeedResourceDetailDto;
import com.example.experiment_service.dto.FlexsimSortPlanDetailDto;
import com.example.experiment_service.dto.FlexsimZoneResourceDetailDto;
import com.example.experiment_service.dto.InfeedResourceDetailDto;
import com.example.experiment_service.dto.SortPlanDetailDto;
import com.example.experiment_service.dto.ZoneResourceDetailDto;
import com.example.experiment_service.security.AuthenticatedUser;
import com.example.experiment_service.service.ResourceDetailsService;
import java.util.List;
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
 * The URLs for the zone, infeed and sort plan lists of an experiment. Same URLs as the old app.
 *
 * WHAT CHANGED:
 *  - The old "getXDetails" URLs also moved the run stage forward and sent a live progress event.
 *    That belongs to simulation-service now, so here they are plain reads. The "clone/..." URLs
 *    do the same thing; we keep both so no caller breaks.
 *  - The old "getXDetails" URLs were open to everybody (no login) so FlexSim could call them.
 *    Now they need a login. The FlexSim worker gets its own service token in a later step.
 */
@RestController
@RequestMapping("/api/resourceDetails")
@PreAuthorize("hasAnyRole('OPERATOR','ADMIN','DEVELOPER')")
public class ResourceDetailsController {

    private final ResourceDetailsService service;

    public ResourceDetailsController(ResourceDetailsService service) {
        this.service = service;
    }

    // ---- zone ----

    @PostMapping("/setZoneDetails")
    public ResponseEntity<String> setZoneDetails(
            @RequestBody List<ZoneResourceDetailDto> dtos, @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(service.saveZoneDetails(dtos, user));
    }

    @GetMapping("/getZoneDetails/{exp_id}")
    public ResponseEntity<List<FlexsimZoneResourceDetailDto>> getZoneDetails(@PathVariable("exp_id") Long experimentId) {
        return ResponseEntity.ok(service.getZoneDetails(experimentId));
    }

    @GetMapping("/clone/getZoneDetails/{exp_id}")
    public ResponseEntity<List<FlexsimZoneResourceDetailDto>> getZoneDetailsForClone(@PathVariable("exp_id") Long experimentId) {
        return ResponseEntity.ok(service.getZoneDetails(experimentId));
    }

    // ---- infeed ----

    @PostMapping("/setInfeedDetails")
    public ResponseEntity<String> setInfeedDetails(
            @RequestBody List<InfeedResourceDetailDto> dtos, @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(service.saveInfeedDetails(dtos, user));
    }

    @GetMapping("/getInfeedDetails/{exp_id}")
    public ResponseEntity<List<FlexsimInfeedResourceDetailDto>> getInfeedDetails(@PathVariable("exp_id") Long experimentId) {
        return ResponseEntity.ok(service.getInfeedDetails(experimentId));
    }

    @GetMapping("/clone/getInfeedDetails/{exp_id}")
    public ResponseEntity<List<FlexsimInfeedResourceDetailDto>> getInfeedDetailsForClone(@PathVariable("exp_id") Long experimentId) {
        return ResponseEntity.ok(service.getInfeedDetails(experimentId));
    }

    // ---- sort plan ----

    @PostMapping("/setSortPlanDetails")
    public ResponseEntity<String> setSortPlanDetails(
            @RequestBody List<SortPlanDetailDto> dtos, @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(service.saveSortPlanDetails(dtos, user));
    }

    @GetMapping("/getSortPlanDetails/{exp_id}")
    public ResponseEntity<List<FlexsimSortPlanDetailDto>> getSortPlanDetails(@PathVariable("exp_id") Long experimentId) {
        return ResponseEntity.ok(service.getSortPlanDetails(experimentId));
    }

    @GetMapping("/clone/getSortPlanDetails/{exp_id}")
    public ResponseEntity<List<FlexsimSortPlanDetailDto>> getSortPlanDetailsForClone(@PathVariable("exp_id") Long experimentId) {
        return ResponseEntity.ok(service.getSortPlanDetails(experimentId));
    }
}
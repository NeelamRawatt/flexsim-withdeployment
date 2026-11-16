package com.example.experiment_service.controller;

import com.example.experiment_service.dto.FlexsimParcelDto;
import com.example.experiment_service.dto.ParcelCountRequestDto;
import com.example.experiment_service.dto.ParcelCountResponseDto;
import com.example.experiment_service.dto.SortPlanResponseDto;
import com.example.experiment_service.service.ParcelService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The parcel URLs. Same URLs and JSON as the old app.
 *
 *   GET  /api/parcels/getParcels/{exp_id}   the parcels FlexSim simulates   (the worker calls this)
 *   POST /api/parcels/getParcelCount        how many parcels in a time window  (the screen)
 *   POST /api/parcels/getSortPlan           parcels per sort name in a window  (the screen)
 *
 * WHAT CHANGED: the old getParcels also moved the run stage forward. The worker does that now.
 * WORKER may only use getParcels (the rule on the method replaces the class rule).
 */
@RestController
@RequestMapping("/api/parcels")
@PreAuthorize("hasAnyRole('OPERATOR','ADMIN','DEVELOPER')")
public class ParcelController {

    private final ParcelService service;

    public ParcelController(ParcelService service) {
        this.service = service;
    }

    @GetMapping("/getParcels/{exp_id}")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','DEVELOPER','WORKER')")
    public ResponseEntity<List<FlexsimParcelDto>> getParcels(@PathVariable("exp_id") Long experimentId) {
        return ResponseEntity.ok(service.getParcelsForExperiment(experimentId));
    }

    @PostMapping("/getParcelCount")
    public ResponseEntity<ParcelCountResponseDto> getParcelCount(@Valid @RequestBody ParcelCountRequestDto request) {
        return ResponseEntity.ok(service.getParcelCount(request));
    }

    @PostMapping("/getSortPlan")
    public ResponseEntity<SortPlanResponseDto> getSortPlan(@Valid @RequestBody ParcelCountRequestDto request) {
        return ResponseEntity.ok(service.getSortPlan(request));
    }
}

package com.example.simulation_service.controller;

import com.example.simulation_service.dto.RunStatusUpdateRequest;
import com.example.simulation_service.dto.SimulationRunDto;
import com.example.simulation_service.dto.StartSimulationRequest;
import com.example.simulation_service.security.AuthenticatedUser;
import com.example.simulation_service.security.JwtAuthenticationFilter;
import com.example.simulation_service.service.SimulationRunService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
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
 * The URLs for runs. Rules are in SimulationRunService.
 *
 * POST startSimulation answers 202 ACCEPTED (not 200/201): "your job is accepted and QUEUED",
 * the simulation itself is not finished yet. Same URL as the old app, so React keeps working.
 *
 * Status updates come from the flexsim-worker. It will log in with its own SERVICE account,
 * role WORKER (added in auth-service in the worker step). Until then ADMIN / DEVELOPER may
 * call it too, so you can test it in Postman.
 */
@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationRunService service;

    public SimulationController(SimulationRunService service) {
        this.service = service;
    }

    @PostMapping("/startSimulation")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','DEVELOPER')")
    public ResponseEntity<SimulationRunDto> start(@Valid @RequestBody StartSimulationRequest body,
                                                  @AuthenticationPrincipal AuthenticatedUser user,
                                                  HttpServletRequest request) {
        SimulationRunDto run = service.start(body.experimentId(), user, JwtAuthenticationFilter.resolveToken(request));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(run);
    }

    @PostMapping("/runs/{runId}/status")
    @PreAuthorize("hasAnyRole('WORKER','ADMIN','DEVELOPER')")
    public SimulationRunDto updateStatus(@PathVariable Long runId, @Valid @RequestBody RunStatusUpdateRequest body) {
        return service.updateStatus(runId, body);
    }

    @PostMapping("/runs/{runId}/cancel")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','DEVELOPER')")
    public SimulationRunDto cancel(@PathVariable Long runId, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.cancel(runId, user);
    }

    @GetMapping("/runs/{runId}")
    public SimulationRunDto get(@PathVariable Long runId) {
        return service.get(runId);
    }

    @GetMapping("/runs/byExperiment/{experimentId}")
    public List<SimulationRunDto> listForExperiment(@PathVariable Long experimentId) {
        return service.listForExperiment(experimentId);
    }

    @GetMapping("/runs/latest/{experimentId}")
    public SimulationRunDto latest(@PathVariable Long experimentId) {
        return service.latestForExperiment(experimentId);
    }
}
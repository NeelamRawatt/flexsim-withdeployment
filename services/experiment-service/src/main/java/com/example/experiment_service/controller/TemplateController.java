package com.example.experiment_service.controller;

import com.example.experiment_service.dto.TemplateItemDto;
import com.example.experiment_service.dto.TemplateListResponseDto;
import com.example.experiment_service.dto.TemplateSaveRequestDto;
import com.example.experiment_service.security.AuthenticatedUser;
import com.example.experiment_service.service.ResourceTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The URLs for saved templates. Same URLs as the old app.
 *
 *  GET    /api/templates      any logged-in user (OPERATOR, VIEWER, ADMIN, DEVELOPER)
 *  POST   /api/templates      ADMIN or DEVELOPER only
 *  DELETE /api/templates/{id} ADMIN or DEVELOPER only, and only your own template
 */
@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final ResourceTemplateService service;

    public TemplateController(ResourceTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public TemplateListResponseDto list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<TemplateItemDto> create(
            @RequestBody TemplateSaveRequestDto request, @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(service.save(request, user));
    }

    @DeleteMapping("/{templateId}")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<Void> delete(@PathVariable("templateId") Long templateId, @AuthenticationPrincipal AuthenticatedUser user) {
        service.delete(templateId, user);
        return ResponseEntity.noContent().build();
    }
}
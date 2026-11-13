package com.ai.sdlc.controller;

import com.ai.sdlc.dto.RequirementGenerationResponse;
import com.ai.sdlc.dto.RequirementResponse;
import com.ai.sdlc.dto.RequirementUpdateRequest;
import com.ai.sdlc.service.RequirementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/requirements")
public class RequirementController {
    private final RequirementService requirementService;

    public RequirementController(RequirementService requirementService) {
        this.requirementService = requirementService;
    }

    @PostMapping("/generate")
    public ResponseEntity<RequirementGenerationResponse> generate(
            @PathVariable UUID projectId,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(requirementService.generate(projectId, authentication.getName()));
    }

    @GetMapping
    public ResponseEntity<List<RequirementResponse>> list(
            @PathVariable UUID projectId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(requirementService.list(projectId, authentication.getName()));
    }

    @GetMapping("/{requirementId}")
    public ResponseEntity<RequirementResponse> get(
            @PathVariable UUID projectId,
            @PathVariable UUID requirementId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(requirementService.get(projectId, requirementId, authentication.getName()));
    }

    @PutMapping("/{requirementId}")
    public ResponseEntity<RequirementResponse> update(
            @PathVariable UUID projectId,
            @PathVariable UUID requirementId,
            @Valid @RequestBody RequirementUpdateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(requirementService.update(projectId, requirementId, request, authentication.getName()));
    }

    @PostMapping("/{requirementId}/approve")
    public ResponseEntity<RequirementResponse> approve(
            @PathVariable UUID projectId,
            @PathVariable UUID requirementId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(requirementService.approve(projectId, requirementId, authentication.getName()));
    }

    @PostMapping("/{requirementId}/reject")
    public ResponseEntity<RequirementResponse> reject(
            @PathVariable UUID projectId,
            @PathVariable UUID requirementId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(requirementService.reject(projectId, requirementId, authentication.getName()));
    }

    @DeleteMapping("/{requirementId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID projectId,
            @PathVariable UUID requirementId,
            Authentication authentication
    ) {
        requirementService.delete(projectId, requirementId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}

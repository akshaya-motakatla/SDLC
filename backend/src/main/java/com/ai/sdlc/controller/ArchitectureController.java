package com.ai.sdlc.controller;

import com.ai.sdlc.dto.ArchitectureDocumentResponse;
import com.ai.sdlc.dto.ArchitectureGenerationRequest;
import com.ai.sdlc.dto.ArchitectureUpdateRequest;
import com.ai.sdlc.service.ArchitectureService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/projects/{projectId}/architecture")
public class ArchitectureController {
    private final ArchitectureService architectureService;

    public ArchitectureController(ArchitectureService architectureService) {
        this.architectureService = architectureService;
    }

    @PostMapping("/generate")
    public ResponseEntity<ArchitectureDocumentResponse> generate(
            @PathVariable UUID projectId,
            @Valid @RequestBody ArchitectureGenerationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(architectureService.generate(projectId, request, authentication.getName()));
    }

    @GetMapping
    public ResponseEntity<List<ArchitectureDocumentResponse>> list(
            @PathVariable UUID projectId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(architectureService.list(projectId, authentication.getName()));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<ArchitectureDocumentResponse> get(
            @PathVariable UUID projectId,
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(architectureService.get(projectId, documentId, authentication.getName()));
    }

    @GetMapping("/{documentId}/versions")
    public ResponseEntity<List<ArchitectureDocumentResponse>> versions(
            @PathVariable UUID projectId,
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(architectureService.versions(projectId, documentId, authentication.getName()));
    }

    @PutMapping("/{documentId}")
    public ResponseEntity<ArchitectureDocumentResponse> update(
            @PathVariable UUID projectId,
            @PathVariable UUID documentId,
            @Valid @RequestBody ArchitectureUpdateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(architectureService.update(projectId, documentId, request, authentication.getName()));
    }

    @PostMapping("/{documentId}/approve")
    public ResponseEntity<ArchitectureDocumentResponse> approve(
            @PathVariable UUID projectId,
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(architectureService.approve(projectId, documentId, authentication.getName()));
    }

    @PostMapping("/{documentId}/reject")
    public ResponseEntity<ArchitectureDocumentResponse> reject(
            @PathVariable UUID projectId,
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(architectureService.reject(projectId, documentId, authentication.getName()));
    }
}

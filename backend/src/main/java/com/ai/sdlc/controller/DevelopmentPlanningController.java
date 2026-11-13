package com.ai.sdlc.controller;

import com.ai.sdlc.domain.TaskCategory;
import com.ai.sdlc.domain.TaskPriority;
import com.ai.sdlc.domain.TaskStatus;
import com.ai.sdlc.dto.DevelopmentTaskGenerationResponse;
import com.ai.sdlc.dto.DevelopmentTaskResponse;
import com.ai.sdlc.dto.DevelopmentTaskStatusRequest;
import com.ai.sdlc.dto.DevelopmentTaskUpdateRequest;
import com.ai.sdlc.service.DevelopmentPlanningService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/development-tasks")
public class DevelopmentPlanningController {
    private final DevelopmentPlanningService developmentPlanningService;

    public DevelopmentPlanningController(DevelopmentPlanningService developmentPlanningService) {
        this.developmentPlanningService = developmentPlanningService;
    }

    @PostMapping("/generate")
    public ResponseEntity<DevelopmentTaskGenerationResponse> generate(
            @PathVariable UUID projectId,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(developmentPlanningService.generate(projectId, authentication.getName()));
    }

    @GetMapping
    public ResponseEntity<List<DevelopmentTaskResponse>> list(
            @PathVariable UUID projectId,
            @RequestParam(required = false) TaskCategory category,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) String milestone,
            Authentication authentication
    ) {
        return ResponseEntity.ok(developmentPlanningService.list(
                projectId,
                authentication.getName(),
                category,
                priority,
                status,
                milestone
        ));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<DevelopmentTaskResponse> get(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(developmentPlanningService.get(projectId, taskId, authentication.getName()));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<DevelopmentTaskResponse> update(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @Valid @RequestBody DevelopmentTaskUpdateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(developmentPlanningService.update(
                projectId,
                taskId,
                request,
                authentication.getName()
        ));
    }

    @PutMapping("/{taskId}/status")
    public ResponseEntity<DevelopmentTaskResponse> updateStatus(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @Valid @RequestBody DevelopmentTaskStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(developmentPlanningService.updateStatus(
                projectId,
                taskId,
                request.status(),
                authentication.getName()
        ));
    }

    @PostMapping("/{taskId}/approve")
    public ResponseEntity<DevelopmentTaskResponse> approve(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(developmentPlanningService.approve(projectId, taskId, authentication.getName()));
    }

    @PostMapping("/{taskId}/reject")
    public ResponseEntity<DevelopmentTaskResponse> reject(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(developmentPlanningService.reject(projectId, taskId, authentication.getName()));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            Authentication authentication
    ) {
        developmentPlanningService.delete(projectId, taskId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}

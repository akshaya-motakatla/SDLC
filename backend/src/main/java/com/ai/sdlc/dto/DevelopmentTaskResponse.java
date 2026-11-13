package com.ai.sdlc.dto;

import com.ai.sdlc.domain.TaskCategory;
import com.ai.sdlc.domain.TaskPriority;
import com.ai.sdlc.domain.TaskReviewStatus;
import com.ai.sdlc.domain.TaskStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DevelopmentTaskResponse(
        UUID id,
        UUID projectId,
        String taskId,
        String title,
        String description,
        TaskCategory category,
        TaskPriority priority,
        Integer estimatedEffortHours,
        List<UUID> dependencyIds,
        List<String> dependencyTaskIds,
        List<TaskRequirementReference> relatedRequirements,
        List<TaskArchitectureReference> architectureReferences,
        String milestone,
        List<String> acceptanceCriteria,
        TaskStatus status,
        TaskReviewStatus reviewStatus,
        String generationProvider,
        boolean demo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

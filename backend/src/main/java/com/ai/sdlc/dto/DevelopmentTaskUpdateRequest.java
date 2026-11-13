package com.ai.sdlc.dto;

import com.ai.sdlc.domain.TaskCategory;
import com.ai.sdlc.domain.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record DevelopmentTaskUpdateRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 5000) String description,
        @NotNull TaskCategory category,
        @NotNull TaskPriority priority,
        @NotNull @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(400) Integer estimatedEffortHours,
        @Size(max = 100) String milestone,
        @NotEmpty @Size(max = 10) List<@NotBlank @Size(max = 500) String> acceptanceCriteria,
        @NotEmpty @Size(max = 30) List<@NotNull UUID> relatedRequirementIds,
        @NotNull @Size(max = 30) List<@NotNull UUID> architectureReferenceIds,
        @NotNull @Size(max = 20) List<@NotNull UUID> dependencyIds
) {
}

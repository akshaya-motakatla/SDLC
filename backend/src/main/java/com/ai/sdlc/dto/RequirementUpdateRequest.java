package com.ai.sdlc.dto;

import com.ai.sdlc.domain.RequirementPriority;
import com.ai.sdlc.domain.RequirementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RequirementUpdateRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String description,
        @NotNull RequirementType type,
        @NotNull RequirementPriority priority,
        @NotEmpty List<@NotBlank @Size(max = 500) String> acceptanceCriteria
) {
}

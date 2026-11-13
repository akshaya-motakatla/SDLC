package com.ai.sdlc.dto;

import com.ai.sdlc.domain.RequirementPriority;
import com.ai.sdlc.domain.RequirementStatus;
import com.ai.sdlc.domain.RequirementType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RequirementResponse(
        UUID id,
        UUID projectId,
        String code,
        String title,
        String description,
        RequirementType type,
        RequirementPriority priority,
        List<String> acceptanceCriteria,
        RequirementStatus status,
        String source,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

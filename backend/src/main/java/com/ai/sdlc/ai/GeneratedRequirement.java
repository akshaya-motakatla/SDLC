package com.ai.sdlc.ai;

import com.ai.sdlc.domain.RequirementPriority;
import com.ai.sdlc.domain.RequirementType;

import java.util.List;

public record GeneratedRequirement(
        String title,
        String description,
        RequirementType type,
        RequirementPriority priority,
        List<String> acceptanceCriteria
) {
}

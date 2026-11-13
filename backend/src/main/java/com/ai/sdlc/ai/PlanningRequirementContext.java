package com.ai.sdlc.ai;

import java.util.List;

public record PlanningRequirementContext(
        String code,
        String title,
        String description,
        String type,
        String priority,
        List<String> acceptanceCriteria
) {
}

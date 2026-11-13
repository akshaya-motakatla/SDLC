package com.ai.sdlc.ai;

import com.ai.sdlc.domain.TaskCategory;
import com.ai.sdlc.domain.TaskPriority;

import java.util.List;
import java.util.UUID;

public record GeneratedDevelopmentTask(
        String title,
        String description,
        TaskCategory category,
        TaskPriority priority,
        int estimatedEffortHours,
        String milestone,
        List<String> acceptanceCriteria,
        List<String> relatedRequirementCodes,
        List<UUID> architectureReferenceIds,
        List<Integer> dependencyIndexes
) {
}

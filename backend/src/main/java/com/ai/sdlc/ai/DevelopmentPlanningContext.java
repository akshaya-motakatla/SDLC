package com.ai.sdlc.ai;

import java.util.List;
import java.util.UUID;

public record DevelopmentPlanningContext(
        UUID projectId,
        String projectName,
        String description,
        String objectives,
        String domain,
        String preferredStack,
        List<PlanningRequirementContext> requirements,
        List<PlanningArchitectureContext> architectures
) {
}

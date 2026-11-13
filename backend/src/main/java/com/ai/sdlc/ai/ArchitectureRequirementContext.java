package com.ai.sdlc.ai;

import java.util.List;
import java.util.UUID;

public record ArchitectureRequirementContext(
        UUID id,
        String code,
        String title,
        String description,
        String type,
        String priority,
        List<String> acceptanceCriteria
) {
}

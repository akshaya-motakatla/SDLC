package com.ai.sdlc.ai;

import java.util.UUID;

public record PlanningArchitectureContext(
        UUID id,
        Integer version,
        String title,
        String architectureStyle,
        String content
) {
}

package com.ai.sdlc.ai;

public record RequirementContext(
        String projectName,
        String description,
        String objectives,
        String domain,
        String preferredStack
) {
}

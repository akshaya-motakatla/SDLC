package com.ai.sdlc.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String description,
        String objectives,
        String domain,
        String preferredStack,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

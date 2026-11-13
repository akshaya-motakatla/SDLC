package com.ai.sdlc.dto;

import com.ai.sdlc.domain.ArchitectureStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ArchitectureDocumentResponse(
        UUID id,
        UUID projectId,
        List<UUID> sourceRequirementIds,
        String title,
        String content,
        String architectureStyle,
        Map<String, String> diagrams,
        ArchitectureStatus status,
        int version,
        String provider,
        boolean demo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

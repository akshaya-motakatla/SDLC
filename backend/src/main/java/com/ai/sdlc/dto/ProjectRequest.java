package com.ai.sdlc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectRequest(
        @NotBlank(message = "Project name is required")
        @Size(max = 255, message = "Project name must be at most 255 characters")
        String name,

        @NotBlank(message = "Project description is required")
        String description,

        String objectives,

        @NotBlank(message = "Project domain is required")
        @Size(max = 100, message = "Domain must be at most 100 characters")
        String domain,

        @NotBlank(message = "Preferred technology stack is required")
        @Size(max = 255, message = "Preferred stack must be at most 255 characters")
        String preferredStack,

        @NotBlank(message = "Project status is required")
        @Size(max = 50, message = "Project status must be at most 50 characters")
        String status
) {
}

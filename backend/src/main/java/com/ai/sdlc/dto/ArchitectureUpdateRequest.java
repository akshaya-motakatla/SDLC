package com.ai.sdlc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record ArchitectureUpdateRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 120) String architectureStyle,
        @NotBlank @Size(max = 50_000) String content,
        @NotEmpty @Size(max = 4) Map<@NotBlank String, @NotBlank @Size(max = 12_000) String> diagrams
) {
}

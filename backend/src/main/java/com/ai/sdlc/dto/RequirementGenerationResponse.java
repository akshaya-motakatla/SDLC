package com.ai.sdlc.dto;

import java.util.List;

public record RequirementGenerationResponse(
        String provider,
        boolean demo,
        List<RequirementResponse> requirements
) {
}

package com.ai.sdlc.dto;

import java.util.List;

public record DevelopmentTaskGenerationResponse(
        String provider,
        boolean demo,
        List<DevelopmentTaskResponse> tasks
) {
}

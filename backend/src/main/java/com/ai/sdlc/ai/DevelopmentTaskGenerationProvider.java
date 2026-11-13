package com.ai.sdlc.ai;

public interface DevelopmentTaskGenerationProvider extends AiGenerationProvider {
    String generate(DevelopmentPlanningContext context);
}

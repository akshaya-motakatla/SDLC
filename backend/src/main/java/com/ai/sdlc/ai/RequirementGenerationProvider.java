package com.ai.sdlc.ai;

public interface RequirementGenerationProvider extends AiGenerationProvider {
    String generate(RequirementContext context);
}

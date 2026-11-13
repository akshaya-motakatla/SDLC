package com.ai.sdlc.ai;

public interface ArchitectureGenerationProvider extends AiGenerationProvider {
    String generate(ArchitectureContext context);
}

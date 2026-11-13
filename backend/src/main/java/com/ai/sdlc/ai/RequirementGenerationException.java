package com.ai.sdlc.ai;

public class RequirementGenerationException extends RuntimeException {
    public RequirementGenerationException(String message) {
        super(message);
    }

    public RequirementGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

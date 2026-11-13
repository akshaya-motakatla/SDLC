package com.ai.sdlc.dto;

import com.ai.sdlc.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record DevelopmentTaskStatusRequest(@NotNull TaskStatus status) {
}

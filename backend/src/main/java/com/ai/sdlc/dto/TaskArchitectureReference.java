package com.ai.sdlc.dto;

import com.ai.sdlc.domain.ArchitectureStatus;

import java.util.UUID;

public record TaskArchitectureReference(UUID id, String title, Integer version, String architectureStyle, ArchitectureStatus status) {
}

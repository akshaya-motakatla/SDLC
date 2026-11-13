package com.ai.sdlc.repository;

import com.ai.sdlc.domain.RequirementTraceability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RequirementTraceabilityRepository extends JpaRepository<RequirementTraceability, UUID> {
    boolean existsByDevelopmentTask_Id(UUID developmentTaskId);
}

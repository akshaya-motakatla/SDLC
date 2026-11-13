package com.ai.sdlc.repository;

import com.ai.sdlc.domain.DevelopmentTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DevelopmentTaskRepository extends JpaRepository<DevelopmentTask, UUID> {
    List<DevelopmentTask> findAllByProject_IdAndProject_User_EmailOrderByCreatedAtAsc(UUID projectId, String email);

    Optional<DevelopmentTask> findByIdAndProject_IdAndProject_User_Email(UUID id, UUID projectId, String email);

    boolean existsByRelatedRequirements_IdAndProject_Id(UUID requirementId, UUID projectId);
}

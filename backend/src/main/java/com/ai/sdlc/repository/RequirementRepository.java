package com.ai.sdlc.repository;

import com.ai.sdlc.domain.Requirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequirementRepository extends JpaRepository<Requirement, UUID> {
    List<Requirement> findAllByProject_IdAndProject_User_EmailOrderByCreatedAtDesc(UUID projectId, String email);

    List<Requirement> findAllByIdInAndProject_IdAndProject_User_EmailAndStatusOrderByCodeAsc(
            List<UUID> ids,
            UUID projectId,
            String email,
            String status
    );

    List<Requirement> findAllByProject_IdAndProject_User_EmailAndStatusOrderByCodeAsc(
            UUID projectId,
            String email,
            String status
    );

    List<Requirement> findAllByIdInAndProject_IdAndProject_User_Email(
            List<UUID> ids,
            UUID projectId,
            String email
    );

    Optional<Requirement> findByIdAndProject_IdAndProject_User_Email(UUID id, UUID projectId, String email);
}

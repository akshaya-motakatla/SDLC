package com.ai.sdlc.repository;

import com.ai.sdlc.domain.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findAllByUser_Email(String email);
    Optional<Project> findByIdAndUser_Email(UUID id, String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select project from Project project where project.id = :projectId and project.user.email = :email")
    Optional<Project> findOwnedForArchitectureGeneration(@Param("projectId") UUID projectId, @Param("email") String email);
    boolean existsByIdAndUser_Email(UUID id, String email);
    void deleteByIdAndUser_Email(UUID id, String email);
}

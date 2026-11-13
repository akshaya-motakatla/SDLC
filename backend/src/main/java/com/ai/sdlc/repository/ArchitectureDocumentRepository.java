package com.ai.sdlc.repository;

import com.ai.sdlc.domain.ArchitectureDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArchitectureDocumentRepository extends JpaRepository<ArchitectureDocument, UUID> {
    @Query("""
            select document
            from ArchitectureDocument document
            where document.project.id = :projectId
              and document.project.user.email = :ownerEmail
            order by case when document.version is null then 0 else 1 end desc,
                     document.version desc,
                     document.createdAt desc
            """)
    List<ArchitectureDocument> findAllByProject_IdAndProject_User_EmailOrderByVersionDescCreatedAtDesc(
            @Param("projectId")
            UUID projectId,
            @Param("ownerEmail")
            String ownerEmail
    );

    Optional<ArchitectureDocument> findByIdAndProject_IdAndProject_User_Email(
            UUID id,
            UUID projectId,
            String ownerEmail
    );

    @Query("""
            select case when count(document) > 0 then true else false end
            from ArchitectureDocument document
            join document.sourceRequirementIds sourceRequirementId
            where document.project.id = :projectId
              and sourceRequirementId = :requirementId
            """)
    boolean existsByProjectAndSourceRequirementId(
            @Param("projectId") UUID projectId,
            @Param("requirementId") UUID requirementId
    );

    @Query("""
            select coalesce(max(document.version), 0)
            from ArchitectureDocument document
            where document.project.id = :projectId
              and document.project.user.email = :ownerEmail
            """)
    int findMaxVersionForOwnedProject(@Param("projectId") UUID projectId, @Param("ownerEmail") String ownerEmail);
}

package com.ai.sdlc.service;

import com.ai.sdlc.ai.ArchitectureGenerationException;
import com.ai.sdlc.ai.GeneratedArchitecture;
import com.ai.sdlc.domain.ArchitectureDocument;
import com.ai.sdlc.domain.ArchitectureStatus;
import com.ai.sdlc.domain.Project;
import com.ai.sdlc.domain.Requirement;
import com.ai.sdlc.exception.NotFoundException;
import com.ai.sdlc.repository.ArchitectureDocumentRepository;
import com.ai.sdlc.repository.ProjectRepository;
import com.ai.sdlc.repository.RequirementRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class ArchitectureDocumentWriter {
    private final ProjectRepository projectRepository;
    private final RequirementRepository requirementRepository;
    private final ArchitectureDocumentRepository architectureDocumentRepository;
    private final ObjectMapper objectMapper;

    public ArchitectureDocumentWriter(
            ProjectRepository projectRepository,
            RequirementRepository requirementRepository,
            ArchitectureDocumentRepository architectureDocumentRepository,
            ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.requirementRepository = requirementRepository;
        this.architectureDocumentRepository = architectureDocumentRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ArchitectureDocument saveNewVersion(
            UUID projectId,
            String ownerEmail,
            List<UUID> requirementIds,
            GeneratedArchitecture generated,
            String provider
    ) {
        Project project = projectRepository.findOwnedForArchitectureGeneration(projectId, ownerEmail)
                .orElseThrow(() -> new NotFoundException("Project not found."));
        List<Requirement> approvedRequirements = requirementRepository
                .findAllByIdInAndProject_IdAndProject_User_EmailAndStatusOrderByCodeAsc(
                        requirementIds,
                        projectId,
                        ownerEmail,
                        "APPROVED"
                );
        if (approvedRequirements.size() != requirementIds.size()) {
            throw new NotFoundException("One or more selected approved requirements are no longer available.");
        }

        ArchitectureDocument document = new ArchitectureDocument(
                generated.title(),
                section(generated.content(), "## Project overview", "## Architecture goals"),
                componentsSummary(generated.content()),
                section(generated.content(), "## Database architecture", "## API design and responsibilities"),
                section(generated.content(), "## API design and responsibilities", "## Major components and their interactions"),
                serializeDiagrams(generated),
                ArchitectureStatus.DRAFT.name(),
                project
        );
        document.setContent(generated.content());
        document.setArchitectureStyle(generated.architectureStyle());
        document.setGenerationProvider(provider);
        document.setSourceRequirementIds(new LinkedHashSet<>(requirementIds));
        document.setVersion(architectureDocumentRepository.findMaxVersionForOwnedProject(projectId, ownerEmail) + 1);
        return architectureDocumentRepository.saveAndFlush(document);
    }

    private String serializeDiagrams(GeneratedArchitecture generated) {
        try {
            return objectMapper.writeValueAsString(generated.diagrams());
        } catch (JsonProcessingException exception) {
            throw new ArchitectureGenerationException("Architecture diagrams could not be stored.", exception);
        }
    }

    private String componentsSummary(String content) {
        return String.join("\n\n",
                section(content, "## High-level system architecture", "## Frontend architecture"),
                section(content, "## Frontend architecture", "## Backend architecture"),
                section(content, "## Backend architecture", "## Database architecture"),
                section(content, "## Major components and their interactions", "## Data flow"));
    }

    private String section(String content, String heading, String nextHeading) {
        int start = content.indexOf(heading);
        if (start < 0) return "";
        start += heading.length();
        int end = content.indexOf(nextHeading, start);
        return content.substring(start, end < 0 ? content.length() : end).trim();
    }
}

package com.ai.sdlc.service;

import com.ai.sdlc.ai.ArchitectureContext;
import com.ai.sdlc.ai.ArchitectureGenerationException;
import com.ai.sdlc.ai.ArchitectureGenerationProvider;
import com.ai.sdlc.ai.ArchitectureJsonParser;
import com.ai.sdlc.ai.ArchitectureRequirementContext;
import com.ai.sdlc.ai.GeneratedArchitecture;
import com.ai.sdlc.domain.ArchitectureDocument;
import com.ai.sdlc.domain.ArchitectureStatus;
import com.ai.sdlc.domain.Project;
import com.ai.sdlc.domain.Requirement;
import com.ai.sdlc.domain.RequirementStatus;
import com.ai.sdlc.dto.ArchitectureDocumentResponse;
import com.ai.sdlc.dto.ArchitectureGenerationRequest;
import com.ai.sdlc.dto.ArchitectureUpdateRequest;
import com.ai.sdlc.exception.ApiException;
import com.ai.sdlc.exception.ConflictException;
import com.ai.sdlc.exception.NotFoundException;
import com.ai.sdlc.repository.ArchitectureDocumentRepository;
import com.ai.sdlc.repository.ProjectRepository;
import com.ai.sdlc.repository.RequirementRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ArchitectureService {
    private final ProjectRepository projectRepository;
    private final RequirementRepository requirementRepository;
    private final ArchitectureDocumentRepository architectureDocumentRepository;
    private final ArchitectureDocumentWriter documentWriter;
    private final ArchitectureGenerationProvider generationProvider;
    private final ArchitectureJsonParser jsonParser;
    private final ObjectMapper objectMapper;

    public ArchitectureService(
            ProjectRepository projectRepository,
            RequirementRepository requirementRepository,
            ArchitectureDocumentRepository architectureDocumentRepository,
            ArchitectureDocumentWriter documentWriter,
            ArchitectureGenerationProvider generationProvider,
            ArchitectureJsonParser jsonParser,
            ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.requirementRepository = requirementRepository;
        this.architectureDocumentRepository = architectureDocumentRepository;
        this.documentWriter = documentWriter;
        this.generationProvider = generationProvider;
        this.jsonParser = jsonParser;
        this.objectMapper = objectMapper;
    }

    public ArchitectureDocumentResponse generate(
            UUID projectId,
            ArchitectureGenerationRequest request,
            String ownerEmail
    ) {
        if (new LinkedHashSet<>(request.sourceRequirementIds()).size() != request.sourceRequirementIds().size()) {
            throw new ApiException("Select each approved requirement only once.");
        }
        Project project = projectRepository.findByIdAndUser_Email(projectId, ownerEmail)
                .orElseThrow(() -> new NotFoundException("Project not found."));
        List<Requirement> selected = requirementRepository
                .findAllByIdInAndProject_IdAndProject_User_EmailAndStatusOrderByCodeAsc(
                        request.sourceRequirementIds(),
                        projectId,
                        ownerEmail,
                        RequirementStatus.APPROVED.name()
                );
        if (selected.size() != request.sourceRequirementIds().size()) {
            throw new ApiException("Select only approved requirements from this project.");
        }
        ArchitectureContext context = new ArchitectureContext(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getObjectives(),
                project.getDomain(),
                project.getPreferredStack(),
                selected.stream().map(this::toContext).toList()
        );

        GeneratedArchitecture generated = jsonParser.parse(generationProvider.generate(context));
        ArchitectureDocument saved = documentWriter.saveNewVersion(
                projectId,
                ownerEmail,
                selected.stream().map(Requirement::getId).toList(),
                generated,
                generationProvider.providerName()
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ArchitectureDocumentResponse> list(UUID projectId, String ownerEmail) {
        getOwnedProject(projectId, ownerEmail);
        return architectureDocumentRepository
                .findAllByProject_IdAndProject_User_EmailOrderByVersionDescCreatedAtDesc(projectId, ownerEmail)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ArchitectureDocumentResponse get(UUID projectId, UUID documentId, String ownerEmail) {
        return toResponse(getOwnedDocument(projectId, documentId, ownerEmail));
    }

    @Transactional(readOnly = true)
    public List<ArchitectureDocumentResponse> versions(UUID projectId, UUID documentId, String ownerEmail) {
        ArchitectureDocument document = getOwnedDocument(projectId, documentId, ownerEmail);
        return architectureDocumentRepository
                .findAllByProject_IdAndProject_User_EmailOrderByVersionDescCreatedAtDesc(
                        document.getProject().getId(),
                        ownerEmail
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ArchitectureDocumentResponse update(
            UUID projectId,
            UUID documentId,
            ArchitectureUpdateRequest request,
            String ownerEmail
    ) {
        ArchitectureDocument document = getOwnedDocument(projectId, documentId, ownerEmail);
        requireDraft(document);
        try {
            jsonParser.validateEdited(
                    request.title(),
                    request.architectureStyle(),
                    request.content(),
                    request.diagrams()
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(exception.getMessage());
        }
        document.setTitle(request.title().trim());
        document.setArchitectureStyle(request.architectureStyle().trim());
        document.setContent(request.content().trim());
        document.setDiagrams(serializeDiagrams(request.diagrams()));
        document.setSummary(section(request.content(), "## Project overview", "## Architecture goals"));
        document.setComponents(request.content());
        document.setDatabaseDesign(section(request.content(), "## Database architecture", "## API design and responsibilities"));
        document.setApiEndpoints(section(request.content(), "## API design and responsibilities", "## Major components and their interactions"));
        return toResponse(architectureDocumentRepository.saveAndFlush(document));
    }

    @Transactional
    public ArchitectureDocumentResponse approve(UUID projectId, UUID documentId, String ownerEmail) {
        return updateStatus(projectId, documentId, ArchitectureStatus.APPROVED, ownerEmail);
    }

    @Transactional
    public ArchitectureDocumentResponse reject(UUID projectId, UUID documentId, String ownerEmail) {
        return updateStatus(projectId, documentId, ArchitectureStatus.REJECTED, ownerEmail);
    }

    private ArchitectureDocumentResponse updateStatus(
            UUID projectId,
            UUID documentId,
            ArchitectureStatus status,
            String ownerEmail
    ) {
        ArchitectureDocument document = getOwnedDocument(projectId, documentId, ownerEmail);
        requireDraft(document);
        document.setStatus(status.name());
        return toResponse(architectureDocumentRepository.saveAndFlush(document));
    }

    private void requireDraft(ArchitectureDocument document) {
        if (!ArchitectureStatus.DRAFT.name().equals(document.getStatus())) {
            throw new ConflictException("Only a draft architecture document can be edited or reviewed.");
        }
    }

    private Project getOwnedProject(UUID projectId, String ownerEmail) {
        return projectRepository.findByIdAndUser_Email(projectId, ownerEmail)
                .orElseThrow(() -> new NotFoundException("Project not found."));
    }

    private ArchitectureDocument getOwnedDocument(UUID projectId, UUID documentId, String ownerEmail) {
        return architectureDocumentRepository.findByIdAndProject_IdAndProject_User_Email(documentId, projectId, ownerEmail)
                .orElseThrow(() -> new NotFoundException("Architecture document not found."));
    }

    private ArchitectureRequirementContext toContext(Requirement requirement) {
        List<String> criteria = requirement.getAcceptanceCriteria() == null || requirement.getAcceptanceCriteria().isBlank()
                ? List.of()
                : requirement.getAcceptanceCriteria().lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        return new ArchitectureRequirementContext(
                requirement.getId(),
                requirement.getCode(),
                requirement.getTitle(),
                requirement.getDescription(),
                requirement.getType(),
                requirement.getPriority(),
                criteria
        );
    }

    private ArchitectureDocumentResponse toResponse(ArchitectureDocument document) {
        String content = document.getContent() == null || document.getContent().isBlank()
                ? legacyContent(document)
                : document.getContent();
        Map<String, String> diagrams = readDiagrams(document.getDiagrams());
        String provider = document.getGenerationProvider() == null ? "LEGACY" : document.getGenerationProvider();
        return new ArchitectureDocumentResponse(
                document.getId(),
                document.getProject().getId(),
                document.getSourceRequirementIds().stream().sorted().toList(),
                document.getTitle(),
                content,
                document.getArchitectureStyle() == null ? "Not specified (legacy document)" : document.getArchitectureStyle(),
                diagrams,
                ArchitectureStatus.valueOf(document.getStatus()),
                document.getVersion() == null ? 0 : document.getVersion(),
                provider,
                "MOCK_DEMO".equals(provider),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    private String legacyContent(ArchitectureDocument document) {
        return """
                ## Project overview
                %s

                ## Major components and their interactions
                %s

                ## Database architecture
                %s

                ## API design and responsibilities
                %s
                """.formatted(
                document.getSummary(),
                document.getComponents(),
                document.getDatabaseDesign(),
                document.getApiEndpoints()
        ).trim();
    }

    private Map<String, String> readDiagrams(String diagrams) {
        if (diagrams == null || diagrams.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(diagrams, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            return Map.of("legacy", diagrams);
        }
    }

    private String serializeDiagrams(Map<String, String> diagrams) {
        try {
            return objectMapper.writeValueAsString(diagrams);
        } catch (JsonProcessingException exception) {
            throw new ArchitectureGenerationException("Architecture diagrams could not be stored.", exception);
        }
    }

    private String section(String content, String heading, String nextHeading) {
        int start = content.indexOf(heading);
        if (start < 0) return "";
        start += heading.length();
        int end = content.indexOf(nextHeading, start);
        return content.substring(start, end < 0 ? content.length() : end).trim();
    }
}

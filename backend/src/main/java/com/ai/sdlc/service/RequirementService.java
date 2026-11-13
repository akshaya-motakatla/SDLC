package com.ai.sdlc.service;

import com.ai.sdlc.ai.GeneratedRequirement;
import com.ai.sdlc.ai.RequirementContext;
import com.ai.sdlc.ai.RequirementGenerationProvider;
import com.ai.sdlc.ai.RequirementJsonParser;
import com.ai.sdlc.domain.Project;
import com.ai.sdlc.domain.Requirement;
import com.ai.sdlc.domain.RequirementPriority;
import com.ai.sdlc.domain.RequirementStatus;
import com.ai.sdlc.domain.RequirementType;
import com.ai.sdlc.dto.RequirementGenerationResponse;
import com.ai.sdlc.dto.RequirementResponse;
import com.ai.sdlc.dto.RequirementUpdateRequest;
import com.ai.sdlc.exception.ConflictException;
import com.ai.sdlc.exception.NotFoundException;
import com.ai.sdlc.repository.ArchitectureDocumentRepository;
import com.ai.sdlc.repository.DevelopmentTaskRepository;
import com.ai.sdlc.repository.ProjectRepository;
import com.ai.sdlc.repository.RequirementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class RequirementService {
    private static final Pattern REQUIREMENT_CODE_PATTERN = Pattern.compile("^REQ-(\\d+)$");

    private final ProjectRepository projectRepository;
    private final RequirementRepository requirementRepository;
    private final ArchitectureDocumentRepository architectureDocumentRepository;
    private final DevelopmentTaskRepository developmentTaskRepository;
    private final RequirementGenerationProvider generationProvider;
    private final RequirementJsonParser jsonParser;

    public RequirementService(
            ProjectRepository projectRepository,
            RequirementRepository requirementRepository,
            ArchitectureDocumentRepository architectureDocumentRepository,
            DevelopmentTaskRepository developmentTaskRepository,
            RequirementGenerationProvider generationProvider,
            RequirementJsonParser jsonParser
    ) {
        this.projectRepository = projectRepository;
        this.requirementRepository = requirementRepository;
        this.architectureDocumentRepository = architectureDocumentRepository;
        this.developmentTaskRepository = developmentTaskRepository;
        this.generationProvider = generationProvider;
        this.jsonParser = jsonParser;
    }

    @Transactional
    public RequirementGenerationResponse generate(UUID projectId, String ownerEmail) {
        Project project = getOwnedProject(projectId, ownerEmail);
        RequirementContext context = new RequirementContext(
                project.getName(),
                project.getDescription(),
                project.getObjectives(),
                project.getDomain(),
                project.getPreferredStack()
        );
        List<GeneratedRequirement> generated = jsonParser.parse(generationProvider.generate(context));
        int nextCode = requirementRepository
                .findAllByProject_IdAndProject_User_EmailOrderByCreatedAtDesc(projectId, ownerEmail).stream()
                .mapToInt(requirement -> codeNumber(requirement.getCode()))
                .max()
                .orElse(0) + 1;
        List<Requirement> requirements = IntStream.range(0, generated.size())
                .mapToObj(index -> toEntity(generated.get(index), project, nextCode + index))
                .toList();
        List<RequirementResponse> saved = requirementRepository.saveAllAndFlush(requirements).stream()
                .map(this::toResponse)
                .toList();
        return new RequirementGenerationResponse(generationProvider.providerName(), generationProvider.isDemo(), saved);
    }

    @Transactional(readOnly = true)
    public List<RequirementResponse> list(UUID projectId, String ownerEmail) {
        getOwnedProject(projectId, ownerEmail);
        return requirementRepository
                .findAllByProject_IdAndProject_User_EmailOrderByCreatedAtDesc(projectId, ownerEmail).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RequirementResponse get(UUID projectId, UUID requirementId, String ownerEmail) {
        return toResponse(getOwnedRequirement(projectId, requirementId, ownerEmail));
    }

    @Transactional
    public RequirementResponse update(UUID projectId, UUID requirementId, RequirementUpdateRequest request, String ownerEmail) {
        Requirement requirement = getOwnedRequirement(projectId, requirementId, ownerEmail);
        requirement.setTitle(request.title().trim());
        requirement.setDescription(request.description().trim());
        requirement.setType(request.type().name());
        requirement.setPriority(request.priority().name());
        requirement.setAcceptanceCriteria(String.join("\n", request.acceptanceCriteria().stream().map(String::trim).toList()));
        requirement.setStatus(RequirementStatus.PENDING.name());
        return toResponse(requirementRepository.saveAndFlush(requirement));
    }

    @Transactional
    public RequirementResponse approve(UUID projectId, UUID requirementId, String ownerEmail) {
        return updateStatus(projectId, requirementId, RequirementStatus.APPROVED, ownerEmail);
    }

    @Transactional
    public RequirementResponse reject(UUID projectId, UUID requirementId, String ownerEmail) {
        return updateStatus(projectId, requirementId, RequirementStatus.REJECTED, ownerEmail);
    }

    @Transactional
    public void delete(UUID projectId, UUID requirementId, String ownerEmail) {
        Requirement requirement = getOwnedRequirement(projectId, requirementId, ownerEmail);
        if (!requirement.getUserStories().isEmpty()
                || !requirement.getDevelopmentTasks().isEmpty()
                || !requirement.getTestCases().isEmpty()
                || !requirement.getTraceabilityLinks().isEmpty()
                || architectureDocumentRepository.existsByProjectAndSourceRequirementId(projectId, requirementId)
                || developmentTaskRepository.existsByRelatedRequirements_IdAndProject_Id(requirementId, projectId)) {
            throw new ConflictException("Cannot delete a requirement while linked delivery or architecture artifacts exist.");
        }
        requirementRepository.delete(requirement);
    }

    private RequirementResponse updateStatus(UUID projectId, UUID requirementId, RequirementStatus status, String ownerEmail) {
        Requirement requirement = getOwnedRequirement(projectId, requirementId, ownerEmail);
        requirement.setStatus(status.name());
        return toResponse(requirementRepository.saveAndFlush(requirement));
    }

    private Project getOwnedProject(UUID projectId, String ownerEmail) {
        return projectRepository.findByIdAndUser_Email(projectId, ownerEmail)
                .orElseThrow(() -> new NotFoundException("Project not found."));
    }

    private Requirement getOwnedRequirement(UUID projectId, UUID requirementId, String ownerEmail) {
        return requirementRepository.findByIdAndProject_IdAndProject_User_Email(requirementId, projectId, ownerEmail)
                .orElseThrow(() -> new NotFoundException("Requirement not found."));
    }

    private Requirement toEntity(GeneratedRequirement generated, Project project, int codeNumber) {
        Requirement requirement = new Requirement(
                "REQ-%04d".formatted(codeNumber),
                generated.title(),
                generated.description(),
                generated.type().name(),
                generated.priority().name(),
                generationProvider.providerName(),
                RequirementStatus.PENDING.name(),
                project
        );
        requirement.setAcceptanceCriteria(String.join("\n", generated.acceptanceCriteria()));
        return requirement;
    }

    private RequirementResponse toResponse(Requirement requirement) {
        return new RequirementResponse(
                requirement.getId(),
                requirement.getProject().getId(),
                requirement.getCode(),
                requirement.getTitle(),
                requirement.getDescription(),
                Enum.valueOf(RequirementType.class, requirement.getType()),
                Enum.valueOf(RequirementPriority.class, requirement.getPriority()),
                requirement.getAcceptanceCriteria() == null || requirement.getAcceptanceCriteria().isBlank()
                        ? List.of()
                        : requirement.getAcceptanceCriteria().lines().map(String::trim).filter(line -> !line.isEmpty()).toList(),
                statusValue(requirement.getStatus()),
                requirement.getSource(),
                requirement.getCreatedAt(),
                requirement.getUpdatedAt()
        );
    }

    private int codeNumber(String code) {
        Matcher matcher = REQUIREMENT_CODE_PATTERN.matcher(code);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private RequirementStatus statusValue(String status) {
        return "DRAFT".equalsIgnoreCase(status)
                ? RequirementStatus.PENDING
                : Enum.valueOf(RequirementStatus.class, status);
    }
}

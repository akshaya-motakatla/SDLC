package com.ai.sdlc.service;

import com.ai.sdlc.ai.DevelopmentPlanningContext;
import com.ai.sdlc.ai.DevelopmentTaskGenerationException;
import com.ai.sdlc.ai.DevelopmentTaskGenerationProvider;
import com.ai.sdlc.ai.DevelopmentTaskJsonParser;
import com.ai.sdlc.ai.GeneratedDevelopmentTask;
import com.ai.sdlc.ai.PlanningArchitectureContext;
import com.ai.sdlc.ai.PlanningRequirementContext;
import com.ai.sdlc.domain.ArchitectureDocument;
import com.ai.sdlc.domain.ArchitectureStatus;
import com.ai.sdlc.domain.DevelopmentTask;
import com.ai.sdlc.domain.Project;
import com.ai.sdlc.domain.Requirement;
import com.ai.sdlc.domain.RequirementStatus;
import com.ai.sdlc.domain.TaskCategory;
import com.ai.sdlc.domain.TaskPriority;
import com.ai.sdlc.domain.TaskReviewStatus;
import com.ai.sdlc.domain.TaskStatus;
import com.ai.sdlc.dto.DevelopmentTaskGenerationResponse;
import com.ai.sdlc.dto.DevelopmentTaskResponse;
import com.ai.sdlc.dto.DevelopmentTaskUpdateRequest;
import com.ai.sdlc.dto.TaskArchitectureReference;
import com.ai.sdlc.dto.TaskRequirementReference;
import com.ai.sdlc.exception.ApiException;
import com.ai.sdlc.exception.ConflictException;
import com.ai.sdlc.exception.NotFoundException;
import com.ai.sdlc.repository.ArchitectureDocumentRepository;
import com.ai.sdlc.repository.DevelopmentTaskRepository;
import com.ai.sdlc.repository.ProjectRepository;
import com.ai.sdlc.repository.RequirementRepository;
import com.ai.sdlc.repository.RequirementTraceabilityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DevelopmentPlanningService {
    private static final Pattern TASK_ID_PATTERN = Pattern.compile("^TASK-(\\d+)$");

    private final ProjectRepository projectRepository;
    private final RequirementRepository requirementRepository;
    private final ArchitectureDocumentRepository architectureDocumentRepository;
    private final DevelopmentTaskRepository developmentTaskRepository;
    private final RequirementTraceabilityRepository requirementTraceabilityRepository;
    private final DevelopmentTaskGenerationProvider generationProvider;
    private final DevelopmentTaskJsonParser jsonParser;

    public DevelopmentPlanningService(
            ProjectRepository projectRepository,
            RequirementRepository requirementRepository,
            ArchitectureDocumentRepository architectureDocumentRepository,
            DevelopmentTaskRepository developmentTaskRepository,
            RequirementTraceabilityRepository requirementTraceabilityRepository,
            DevelopmentTaskGenerationProvider generationProvider,
            DevelopmentTaskJsonParser jsonParser
    ) {
        this.projectRepository = projectRepository;
        this.requirementRepository = requirementRepository;
        this.architectureDocumentRepository = architectureDocumentRepository;
        this.developmentTaskRepository = developmentTaskRepository;
        this.requirementTraceabilityRepository = requirementTraceabilityRepository;
        this.generationProvider = generationProvider;
        this.jsonParser = jsonParser;
    }

    @Transactional
    public DevelopmentTaskGenerationResponse generate(UUID projectId, String ownerEmail) {
        Project project = projectRepository.findOwnedForArchitectureGeneration(projectId, ownerEmail)
                .orElseThrow(() -> new NotFoundException("Project not found."));
        List<Requirement> requirements = requirementRepository
                .findAllByProject_IdAndProject_User_EmailAndStatusOrderByCodeAsc(
                        projectId,
                        ownerEmail,
                        RequirementStatus.APPROVED.name()
                );
        if (requirements.isEmpty()) {
            throw new ConflictException("Approve at least one requirement before generating development tasks.");
        }
        List<ArchitectureDocument> architectures = architectureDocumentRepository
                .findAllByProject_IdAndProject_User_EmailAndStatusOrderByVersionDescCreatedAtDesc(
                        projectId,
                        ownerEmail,
                        ArchitectureStatus.APPROVED.name()
                );

        DevelopmentPlanningContext context = new DevelopmentPlanningContext(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getObjectives(),
                project.getDomain(),
                project.getPreferredStack(),
                requirements.stream().map(this::toRequirementContext).toList(),
                architectures.stream().map(this::toArchitectureContext).toList()
        );
        List<GeneratedDevelopmentTask> generated = jsonParser.parse(generationProvider.generate(context));
        Map<String, Requirement> requirementsByCode = new LinkedHashMap<>();
        requirements.forEach(requirement -> requirementsByCode.put(requirement.getCode(), requirement));
        Set<UUID> approvedArchitectureIds = architectures.stream()
                .map(ArchitectureDocument::getId)
                .collect(java.util.stream.Collectors.toSet());
        int nextTaskNumber = nextTaskNumber(projectId, ownerEmail);

        List<DevelopmentTask> tasks = new ArrayList<>();
        for (int index = 0; index < generated.size(); index++) {
            GeneratedDevelopmentTask taskData = generated.get(index);
            List<Requirement> relatedRequirements = taskData.relatedRequirementCodes().stream()
                    .map(requirementsByCode::get)
                    .toList();
            if (relatedRequirements.stream().anyMatch(java.util.Objects::isNull)) {
                throw invalidSourceReference();
            }
            if (!approvedArchitectureIds.containsAll(taskData.architectureReferenceIds())) {
                throw invalidSourceReference();
            }
            DevelopmentTask task = new DevelopmentTask(
                    "TASK-%04d".formatted(nextTaskNumber + index),
                    taskData.title(),
                    taskData.description(),
                    taskData.priority().name(),
                    TaskStatus.TODO.name(),
                    null,
                    taskData.milestone(),
                    project,
                    relatedRequirements.get(0)
            );
            task.setCategory(taskData.category().name());
            task.setEstimatedEffortHours(taskData.estimatedEffortHours());
            task.setAcceptanceCriteria(String.join("\n", taskData.acceptanceCriteria()));
            task.setReviewStatus(TaskReviewStatus.DRAFT.name());
            task.setGenerationProvider(generationProvider.providerName());
            task.setRelatedRequirements(new LinkedHashSet<>(relatedRequirements));
            task.setArchitectureReferences(architectures.stream()
                    .filter(document -> taskData.architectureReferenceIds().contains(document.getId()))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
            tasks.add(task);
        }

        List<DevelopmentTask> saved = developmentTaskRepository.saveAllAndFlush(tasks);
        for (int index = 0; index < saved.size(); index++) {
            Set<DevelopmentTask> dependencies = generated.get(index).dependencyIndexes().stream()
                    .map(saved::get)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            saved.get(index).setDependencies(dependencies);
            saved.get(index).setDependencyIds(dependencies.stream()
                    .map(DevelopmentTask::getTaskId)
                    .sorted()
                    .reduce((left, right) -> left + "," + right)
                    .orElse(""));
        }
        List<DevelopmentTaskResponse> responses = developmentTaskRepository.saveAllAndFlush(saved).stream()
                .map(this::toResponse)
                .toList();
        return new DevelopmentTaskGenerationResponse(
                generationProvider.providerName(),
                generationProvider.isDemo(),
                responses
        );
    }

    @Transactional(readOnly = true)
    public List<DevelopmentTaskResponse> list(
            UUID projectId,
            String ownerEmail,
            TaskCategory category,
            TaskPriority priority,
            TaskStatus status,
            String milestone
    ) {
        getOwnedProject(projectId, ownerEmail);
        String normalizedMilestone = milestone == null ? null : milestone.trim().toLowerCase(Locale.ROOT);
        return developmentTaskRepository
                .findAllByProject_IdAndProject_User_EmailOrderByCreatedAtAsc(projectId, ownerEmail)
                .stream()
                .filter(task -> category == null || category.name().equalsIgnoreCase(task.getCategory()))
                .filter(task -> priority == null || priority.name().equalsIgnoreCase(task.getPriority()))
                .filter(task -> status == null || statusValue(task.getStatus()) == status)
                .filter(task -> normalizedMilestone == null || normalizedMilestone.isEmpty()
                        || task.getMilestone() != null
                        && task.getMilestone().toLowerCase(Locale.ROOT).contains(normalizedMilestone))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DevelopmentTaskResponse get(UUID projectId, UUID taskId, String ownerEmail) {
        return toResponse(getOwnedTask(projectId, taskId, ownerEmail));
    }

    @Transactional
    public DevelopmentTaskResponse update(
            UUID projectId,
            UUID taskId,
            DevelopmentTaskUpdateRequest request,
            String ownerEmail
    ) {
        DevelopmentTask task = getOwnedTask(projectId, taskId, ownerEmail);
        requireDraft(task);
        rejectDuplicates(request.relatedRequirementIds(), "Related requirements must not be repeated.");
        rejectDuplicates(request.architectureReferenceIds(), "Architecture references must not be repeated.");
        rejectDuplicates(request.dependencyIds(), "Task dependencies must not be repeated.");

        List<Requirement> requirements = requirementRepository.findAllByIdInAndProject_IdAndProject_User_Email(
                request.relatedRequirementIds(), projectId, ownerEmail
        );
        if (requirements.size() != request.relatedRequirementIds().size()) throw invalidSourceReference();
        List<ArchitectureDocument> architectures = architectureDocumentRepository
                .findAllByIdInAndProject_IdAndProject_User_Email(
                        request.architectureReferenceIds(), projectId, ownerEmail
                );
        if (architectures.size() != request.architectureReferenceIds().size()) throw invalidSourceReference();
        List<DevelopmentTask> allTasks = developmentTaskRepository
                .findAllByProject_IdAndProject_User_EmailOrderByCreatedAtAsc(projectId, ownerEmail);
        Map<UUID, DevelopmentTask> tasksById = new LinkedHashMap<>();
        allTasks.forEach(existing -> tasksById.put(existing.getId(), existing));
        Set<DevelopmentTask> dependencies = new LinkedHashSet<>();
        for (UUID dependencyId : request.dependencyIds() == null ? List.<UUID>of() : request.dependencyIds()) {
            DevelopmentTask dependency = tasksById.get(dependencyId);
            if (dependency == null || dependency.getId().equals(task.getId())) {
                throw new ApiException("Dependencies must be different tasks in this project.");
            }
            if (dependsOn(dependency, task.getId(), new HashSet<>())) {
                throw new ApiException("This dependency would create a circular task dependency.");
            }
            dependencies.add(dependency);
        }

        task.setTitle(request.title().trim());
        task.setDescription(request.description().trim());
        task.setCategory(request.category().name());
        task.setPriority(request.priority().name());
        task.setEstimatedEffortHours(request.estimatedEffortHours());
        task.setMilestone(request.milestone() == null || request.milestone().isBlank() ? null : request.milestone().trim());
        task.setAcceptanceCriteria(String.join("\n", request.acceptanceCriteria().stream().map(String::trim).toList()));
        task.setRequirement(requirements.get(0));
        task.setRelatedRequirements(new LinkedHashSet<>(requirements));
        task.setArchitectureReferences(new LinkedHashSet<>(architectures));
        task.setDependencies(dependencies);
        task.setDependencyIds(dependencies.stream().map(DevelopmentTask::getTaskId).sorted()
                .reduce((left, right) -> left + "," + right).orElse(""));
        return toResponse(developmentTaskRepository.saveAndFlush(task));
    }

    @Transactional
    public DevelopmentTaskResponse updateStatus(
            UUID projectId,
            UUID taskId,
            TaskStatus status,
            String ownerEmail
    ) {
        DevelopmentTask task = getOwnedTask(projectId, taskId, ownerEmail);
        if (!TaskReviewStatus.APPROVED.name().equalsIgnoreCase(task.getReviewStatus())) {
            throw new ConflictException("Only approved tasks can be moved through the delivery workflow.");
        }
        task.setStatus(status.name());
        return toResponse(developmentTaskRepository.saveAndFlush(task));
    }

    @Transactional
    public DevelopmentTaskResponse approve(UUID projectId, UUID taskId, String ownerEmail) {
        return updateReviewStatus(projectId, taskId, TaskReviewStatus.APPROVED, ownerEmail);
    }

    @Transactional
    public DevelopmentTaskResponse reject(UUID projectId, UUID taskId, String ownerEmail) {
        return updateReviewStatus(projectId, taskId, TaskReviewStatus.REJECTED, ownerEmail);
    }

    @Transactional
    public void delete(UUID projectId, UUID taskId, String ownerEmail) {
        DevelopmentTask task = getOwnedTask(projectId, taskId, ownerEmail);
        if (TaskReviewStatus.APPROVED.name().equalsIgnoreCase(task.getReviewStatus())) {
            throw new ConflictException("Approved tasks cannot be deleted.");
        }
        if (requirementTraceabilityRepository.existsByDevelopmentTask_Id(taskId)) {
            throw new ConflictException("Cannot delete a task linked to requirement traceability.");
        }
        List<DevelopmentTask> tasks = developmentTaskRepository
                .findAllByProject_IdAndProject_User_EmailOrderByCreatedAtAsc(projectId, ownerEmail);
        if (tasks.stream().anyMatch(candidate -> candidate.getDependencies().contains(task))) {
            throw new ConflictException("Cannot delete a task that is a dependency of another task.");
        }
        developmentTaskRepository.delete(task);
    }

    private DevelopmentTaskResponse updateReviewStatus(
            UUID projectId,
            UUID taskId,
            TaskReviewStatus status,
            String ownerEmail
    ) {
        DevelopmentTask task = getOwnedTask(projectId, taskId, ownerEmail);
        requireDraft(task);
        task.setReviewStatus(status.name());
        return toResponse(developmentTaskRepository.saveAndFlush(task));
    }

    private boolean dependsOn(DevelopmentTask task, UUID targetId, Set<UUID> visited) {
        if (!visited.add(task.getId())) return false;
        if (task.getDependencies().stream().anyMatch(dependency -> dependency.getId().equals(targetId))) return true;
        return task.getDependencies().stream().anyMatch(dependency -> dependsOn(dependency, targetId, visited));
    }

    private int nextTaskNumber(UUID projectId, String ownerEmail) {
        return developmentTaskRepository.findAllByProject_IdAndProject_User_EmailOrderByCreatedAtAsc(projectId, ownerEmail)
                .stream()
                .map(DevelopmentTask::getTaskId)
                .map(TASK_ID_PATTERN::matcher)
                .filter(Matcher::matches)
                .mapToInt(matcher -> Integer.parseInt(matcher.group(1)))
                .max()
                .orElse(0) + 1;
    }

    private void requireDraft(DevelopmentTask task) {
        if (!TaskReviewStatus.DRAFT.name().equalsIgnoreCase(task.getReviewStatus())) {
            throw new ConflictException("Only a draft task can be edited or reviewed.");
        }
    }

    private void rejectDuplicates(List<UUID> ids, String message) {
        if (ids != null && new HashSet<>(ids).size() != ids.size()) throw new ApiException(message);
    }

    private Project getOwnedProject(UUID projectId, String ownerEmail) {
        return projectRepository.findByIdAndUser_Email(projectId, ownerEmail)
                .orElseThrow(() -> new NotFoundException("Project not found."));
    }

    private DevelopmentTask getOwnedTask(UUID projectId, UUID taskId, String ownerEmail) {
        return developmentTaskRepository.findByIdAndProject_IdAndProject_User_Email(taskId, projectId, ownerEmail)
                .orElseThrow(() -> new NotFoundException("Development task not found."));
    }

    private PlanningRequirementContext toRequirementContext(Requirement requirement) {
        List<String> criteria = requirement.getAcceptanceCriteria() == null || requirement.getAcceptanceCriteria().isBlank()
                ? List.of()
                : requirement.getAcceptanceCriteria().lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        return new PlanningRequirementContext(
                requirement.getCode(),
                requirement.getTitle(),
                requirement.getDescription(),
                requirement.getType(),
                requirement.getPriority(),
                criteria
        );
    }

    private PlanningArchitectureContext toArchitectureContext(ArchitectureDocument document) {
        return new PlanningArchitectureContext(
                document.getId(),
                document.getVersion(),
                document.getTitle(),
                document.getArchitectureStyle(),
                document.getContent() == null ? document.getSummary() : document.getContent()
        );
    }

    private DevelopmentTaskResponse toResponse(DevelopmentTask task) {
        List<Requirement> requirements = task.getRelatedRequirements().isEmpty()
                ? List.of(task.getRequirement())
                : task.getRelatedRequirements().stream()
                        .sorted(Comparator.comparing(Requirement::getCode))
                        .toList();
        List<DevelopmentTask> dependencies = task.getDependencies().stream()
                .sorted(Comparator.comparing(DevelopmentTask::getTaskId))
                .toList();
        String provider = task.getGenerationProvider() == null ? "LEGACY" : task.getGenerationProvider();
        return new DevelopmentTaskResponse(
                task.getId(),
                task.getProject().getId(),
                task.getTaskId(),
                task.getTitle(),
                task.getDescription(),
                categoryValue(task.getCategory()),
                priorityValue(task.getPriority()),
                task.getEstimatedEffortHours(),
                dependencies.stream().map(DevelopmentTask::getId).toList(),
                dependencies.stream().map(DevelopmentTask::getTaskId).toList(),
                requirements.stream().map(requirement -> new TaskRequirementReference(
                        requirement.getId(),
                        requirement.getCode(),
                        requirement.getTitle()
                )).toList(),
                task.getArchitectureReferences().stream()
                        .sorted(Comparator.comparing(ArchitectureDocument::getVersion, Comparator.nullsLast(Comparator.reverseOrder())))
                        .map(document -> new TaskArchitectureReference(
                                document.getId(),
                                document.getTitle(),
                                document.getVersion(),
                                document.getArchitectureStyle(),
                                ArchitectureStatus.valueOf(document.getStatus())
                        ))
                        .toList(),
                task.getMilestone(),
                task.getAcceptanceCriteria() == null || task.getAcceptanceCriteria().isBlank()
                        ? List.of()
                        : task.getAcceptanceCriteria().lines().map(String::trim).filter(line -> !line.isEmpty()).toList(),
                statusValue(task.getStatus()),
                reviewStatusValue(task.getReviewStatus()),
                provider,
                "MOCK_DEMO".equals(provider),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private TaskCategory categoryValue(String value) {
        return value == null ? TaskCategory.OTHER : TaskCategory.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private TaskPriority priorityValue(String value) {
        return TaskPriority.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private TaskStatus statusValue(String value) {
        return "DONE".equalsIgnoreCase(value) ? TaskStatus.COMPLETED : TaskStatus.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private TaskReviewStatus reviewStatusValue(String value) {
        return value == null ? TaskReviewStatus.DRAFT : TaskReviewStatus.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private ApiException invalidSourceReference() {
        return new ApiException("Referenced requirements and architecture documents must belong to this project.");
    }
}

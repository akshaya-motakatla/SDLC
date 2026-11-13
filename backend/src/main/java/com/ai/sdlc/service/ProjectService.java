package com.ai.sdlc.service;

import com.ai.sdlc.domain.Project;
import com.ai.sdlc.domain.User;
import com.ai.sdlc.dto.ProjectRequest;
import com.ai.sdlc.dto.ProjectResponse;
import com.ai.sdlc.exception.NotFoundException;
import com.ai.sdlc.repository.ProjectRepository;
import com.ai.sdlc.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ProjectResponse createProject(ProjectRequest request, String ownerEmail) {
        User user = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new NotFoundException("Authenticated account could not be found."));

        Project project = new Project(
                request.name(),
                request.description(),
                request.objectives(),
                request.domain(),
                request.preferredStack(),
                request.status(),
                user
        );

        Project saved = projectRepository.saveAndFlush(project);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects(String ownerEmail) {
        return projectRepository.findAllByUser_Email(ownerEmail).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(UUID id, String ownerEmail) {
        Project project = projectRepository.findByIdAndUser_Email(id, ownerEmail)
                .orElseThrow(() -> new NotFoundException("Project not found."));
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse updateProject(UUID id, ProjectRequest request, String ownerEmail) {
        Project project = projectRepository.findByIdAndUser_Email(id, ownerEmail)
                .orElseThrow(() -> new NotFoundException("Project not found."));

        project.setName(request.name());
        project.setDescription(request.description());
        project.setObjectives(request.objectives());
        project.setDomain(request.domain());
        project.setPreferredStack(request.preferredStack());
        project.setStatus(request.status());

        return toResponse(projectRepository.saveAndFlush(project));
    }

    @Transactional
    public void deleteProject(UUID id, String ownerEmail) {
        if (!projectRepository.existsByIdAndUser_Email(id, ownerEmail)) {
            throw new NotFoundException("Project not found.");
        }
        projectRepository.deleteByIdAndUser_Email(id, ownerEmail);
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getObjectives(),
                project.getDomain(),
                project.getPreferredStack(),
                project.getStatus(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}

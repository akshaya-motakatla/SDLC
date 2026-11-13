package com.ai.sdlc;

import com.ai.sdlc.domain.Project;
import com.ai.sdlc.domain.User;
import com.ai.sdlc.dto.ProjectRequest;
import com.ai.sdlc.dto.ProjectResponse;
import com.ai.sdlc.repository.ProjectRepository;
import com.ai.sdlc.repository.UserRepository;
import com.ai.sdlc.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {
    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void createProjectShouldPersistProjectAndReturnResponse() {
        User user = new User("demo-user", "demo@example.com", "hashed-password", "Demo", "User");
        Project project = new Project("Smart Inventory", "Inventory system", "Reduce stock waste", "Retail", "Java + Spring Boot", "ACTIVE", user);

        when(userRepository.findByEmail("demo@example.com")).thenReturn(Optional.of(user));
        when(projectRepository.saveAndFlush(any(Project.class))).thenReturn(project);

        ProjectResponse response = projectService.createProject(
                new ProjectRequest("Smart Inventory", "Inventory system", "Reduce stock waste", "Retail", "Java + Spring Boot", "ACTIVE"),
                "demo@example.com"
        );

        assertNotNull(response);
        assertEquals("Smart Inventory", response.name());
        assertEquals("ACTIVE", response.status());
        verify(projectRepository).saveAndFlush(any(Project.class));
    }

    @Test
    void getAllProjectsShouldReturnListFromRepository() {
        User user = new User("demo-user", "demo@example.com", "hashed-password", "Demo", "User");
        Project project = new Project("Project A", "A project", "Objective", "Finance", "Java", "DRAFT", user);

        when(projectRepository.findAllByUser_Email("demo@example.com")).thenReturn(List.of(project));

        List<ProjectResponse> result = projectService.getAllProjects("demo@example.com");

        assertEquals(1, result.size());
        assertEquals("Project A", result.get(0).name());
    }

    @Test
    void getProjectByIdShouldReturnMatchingProject() {
        User user = new User("demo-user", "demo@example.com", "hashed-password", "Demo", "User");
        Project project = new Project("Project B", "B project", "Objective", "Healthcare", "Spring Boot", "ACTIVE", user);
        UUID id = UUID.randomUUID();

        when(projectRepository.findByIdAndUser_Email(id, "demo@example.com")).thenReturn(Optional.of(project));

        ProjectResponse response = projectService.getProjectById(id, "demo@example.com");

        assertNotNull(response);
        assertEquals("Project B", response.name());
    }

    @Test
    void updateProjectShouldFlushChangesBeforeReturningResponse() {
        User user = new User("demo-user", "demo@example.com", "hashed-password", "Demo", "User");
        Project project = new Project("Project B", "Old description", "Objective", "Healthcare", "Spring Boot", "ACTIVE", user);
        UUID id = UUID.randomUUID();

        when(projectRepository.findByIdAndUser_Email(id, "demo@example.com")).thenReturn(Optional.of(project));
        when(projectRepository.saveAndFlush(project)).thenReturn(project);

        ProjectResponse response = projectService.updateProject(
                id,
                new ProjectRequest("Project B", "Updated description", "Objective", "Healthcare", "Spring Boot", "ACTIVE"),
                "demo@example.com"
        );

        assertEquals("Updated description", response.description());
        verify(projectRepository).saveAndFlush(project);
    }
}

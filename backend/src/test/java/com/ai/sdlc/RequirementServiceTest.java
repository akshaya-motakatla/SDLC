package com.ai.sdlc;

import com.ai.sdlc.ai.MockRequirementGenerationProvider;
import com.ai.sdlc.ai.RequirementGenerationException;
import com.ai.sdlc.ai.RequirementGenerationProvider;
import com.ai.sdlc.ai.RequirementContext;
import com.ai.sdlc.ai.RequirementJsonParser;
import com.ai.sdlc.domain.Project;
import com.ai.sdlc.domain.Requirement;
import com.ai.sdlc.domain.RequirementStatus;
import com.ai.sdlc.domain.User;
import com.ai.sdlc.dto.RequirementUpdateRequest;
import com.ai.sdlc.exception.NotFoundException;
import com.ai.sdlc.repository.ProjectRepository;
import com.ai.sdlc.repository.RequirementRepository;
import com.ai.sdlc.repository.ArchitectureDocumentRepository;
import com.ai.sdlc.service.RequirementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementServiceTest {
    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private RequirementRepository requirementRepository;

    @Mock
    private ArchitectureDocumentRepository architectureDocumentRepository;

    @InjectMocks
    private RequirementService requirementService;

    @Test
    void generationPersistsOnlyValidatedPendingItemsWithDemoLabel() {
        User user = new User("owner", "owner@example.com", "hashed", "Owner", "User");
        Project project = new Project("Workspace", "Description", "Objectives", "Engineering", "Java", "ACTIVE", user);
        when(projectRepository.findByIdAndUser_Email(any(UUID.class), org.mockito.ArgumentMatchers.eq(user.getEmail())))
                .thenReturn(Optional.of(project));
        when(requirementRepository.findAllByProject_IdAndProject_User_EmailOrderByCreatedAtDesc(any(UUID.class), org.mockito.ArgumentMatchers.eq(user.getEmail())))
                .thenReturn(List.of());
        when(requirementRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<Requirement> saved = invocation.getArgument(0);
            return saved;
        });
        RequirementService service = new RequirementService(
                projectRepository,
                requirementRepository,
                architectureDocumentRepository,
                new MockRequirementGenerationProvider(),
                new RequirementJsonParser(new ObjectMapper())
        );

        var response = service.generate(UUID.randomUUID(), user.getEmail());

        assertEquals("MOCK_DEMO", response.provider());
        assertEquals(true, response.demo());
        assertEquals(2, response.requirements().size());
        assertEquals(RequirementStatus.PENDING.name(), response.requirements().get(0).status().name());
        assertEquals("MOCK_DEMO", response.requirements().get(0).source());
        verify(requirementRepository).saveAllAndFlush(any());
    }

    @Test
    void generationDoesNotSaveWhenProjectIsNotOwned() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findByIdAndUser_Email(projectId, "other@example.com")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> requirementService.generate(projectId, "other@example.com"));
        verify(requirementRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void generationDoesNotPersistMalformedProviderOutput() {
        UUID projectId = UUID.randomUUID();
        User user = new User("owner", "owner@example.com", "hashed", "Owner", "User");
        Project project = new Project("Workspace", "Description", "Objectives", "Engineering", "Java", "ACTIVE", user);
        when(projectRepository.findByIdAndUser_Email(projectId, user.getEmail())).thenReturn(Optional.of(project));
        RequirementGenerationProvider invalidProvider = new RequirementGenerationProvider() {
            @Override
            public String generate(RequirementContext context) {
                return "not json";
            }

            @Override
            public String providerName() {
                return "TEST";
            }

            @Override
            public boolean isDemo() {
                return false;
            }
        };
        RequirementService service = new RequirementService(
                projectRepository,
                requirementRepository,
                architectureDocumentRepository,
                invalidProvider,
                new RequirementJsonParser(new ObjectMapper())
        );

        assertThrows(RequirementGenerationException.class, () -> service.generate(projectId, user.getEmail()));
        verify(requirementRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void editingResetsReviewedRequirementToPending() {
        UUID projectId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        User user = new User("owner", "owner@example.com", "hashed", "Owner", "User");
        Project project = new Project("Workspace", "Description", null, "Engineering", "Java", "ACTIVE", user);
        Requirement requirement = new Requirement("REQ-0001", "Old title", "Old description", "FUNCTIONAL", "LOW", "MOCK_DEMO", "APPROVED", project);
        requirement.setAcceptanceCriteria("Old criterion");
        when(requirementRepository.findByIdAndProject_IdAndProject_User_Email(requirementId, projectId, user.getEmail()))
                .thenReturn(Optional.of(requirement));
        when(requirementRepository.saveAndFlush(requirement)).thenReturn(requirement);

        var response = requirementService.update(
                projectId,
                requirementId,
                new RequirementUpdateRequest(
                        "Updated title",
                        "Updated description",
                        com.ai.sdlc.domain.RequirementType.NON_FUNCTIONAL,
                        com.ai.sdlc.domain.RequirementPriority.MEDIUM,
                        List.of("Updated criterion")
                ),
                user.getEmail()
        );

        assertEquals("Updated title", response.title());
        assertEquals(RequirementStatus.PENDING, response.status());
        assertEquals(List.of("Updated criterion"), response.acceptanceCriteria());
        verify(requirementRepository).saveAndFlush(requirement);
    }

    @Test
    void deletingRequirementWithLinkedArtifactsIsBlocked() {
        UUID projectId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        User user = new User("owner", "owner@example.com", "hashed", "Owner", "User");
        Project project = new Project("Workspace", "Description", null, "Engineering", "Java", "ACTIVE", user);
        Requirement requirement = new Requirement("REQ-0001", "Title", "Description", "FUNCTIONAL", "HIGH", "MOCK_DEMO", "PENDING", project);
        requirement.getUserStories().add(new com.ai.sdlc.domain.UserStory());
        when(requirementRepository.findByIdAndProject_IdAndProject_User_Email(requirementId, projectId, user.getEmail()))
                .thenReturn(Optional.of(requirement));

        assertThrows(
                com.ai.sdlc.exception.ConflictException.class,
                () -> requirementService.delete(projectId, requirementId, user.getEmail())
        );
        verify(requirementRepository, never()).delete(requirement);
    }

    @Test
    void deletingRequirementReferencedByArchitectureIsBlocked() {
        UUID projectId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        User user = new User("owner", "owner@example.com", "hashed", "Owner", "User");
        Project project = new Project("Workspace", "Description", null, "Engineering", "Java", "ACTIVE", user);
        Requirement requirement = new Requirement("REQ-0001", "Title", "Description", "FUNCTIONAL", "HIGH", "MOCK_DEMO", "APPROVED", project);
        when(requirementRepository.findByIdAndProject_IdAndProject_User_Email(requirementId, projectId, user.getEmail()))
                .thenReturn(Optional.of(requirement));
        when(architectureDocumentRepository.existsByProjectAndSourceRequirementId(projectId, requirementId))
                .thenReturn(true);

        assertThrows(
                com.ai.sdlc.exception.ConflictException.class,
                () -> requirementService.delete(projectId, requirementId, user.getEmail())
        );
        verify(requirementRepository, never()).delete(requirement);
    }
}

package com.ai.sdlc;

import com.ai.sdlc.repository.ArchitectureDocumentRepository;
import com.ai.sdlc.repository.DevelopmentTaskRepository;
import com.ai.sdlc.repository.ProjectRepository;
import com.ai.sdlc.repository.RequirementRepository;
import com.ai.sdlc.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DevelopmentPlanningIntegrationTest {
    private static final String PASSWORD = "planning-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DevelopmentTaskRepository developmentTaskRepository;

    @Autowired
    private ArchitectureDocumentRepository architectureDocumentRepository;

    @Autowired
    private RequirementRepository requirementRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void clearIsolatedDatabase() {
        developmentTaskRepository.deleteAll();
        architectureDocumentRepository.deleteAll();
        requirementRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerCanGenerateEditReviewFilterAndManageTasks() throws Exception {
        AuthenticatedSession owner = register("planning.owner", "planning.owner@example.com");
        String projectId = createProject(owner);
        String requirementId = createAndApproveRequirement(owner, projectId);
        String architectureId = createAndApproveArchitecture(owner, projectId, requirementId);

        JsonNode generated = objectMapper.readTree(mockMvc.perform(post(
                                "/api/projects/{projectId}/development-tasks/generate", projectId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("MOCK_DEMO"))
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.tasks.length()").value(1))
                .andExpect(jsonPath("$.tasks[0].reviewStatus").value("DRAFT"))
                .andExpect(jsonPath("$.tasks[0].relatedRequirements[0].id").value(requirementId))
                .andExpect(jsonPath("$.tasks[0].architectureReferences[0].id").value(architectureId))
                .andReturn().getResponse().getContentAsString());
        JsonNode task = generated.get("tasks").get(0);
        String taskId = task.get("id").asText();

        mockMvc.perform(get("/api/projects/{projectId}/development-tasks", projectId)
                        .param("category", "BACKEND")
                        .session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/projects/{projectId}/development-tasks", projectId)
                        .param("category", "FRONTEND")
                        .session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/projects/{projectId}/development-tasks/{taskId}", projectId, taskId)
                        .session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value("TASK-0001"));

        String updateBody = """
                {
                  "title": "Implement the project API",
                  "description": "Deliver and verify the approved project workflow.",
                  "category": "API",
                  "priority": "HIGH",
                  "estimatedEffortHours": 10,
                  "milestone": "Milestone 1",
                  "acceptanceCriteria": ["The owner can complete the approved workflow."],
                  "relatedRequirementIds": ["%s"],
                  "architectureReferenceIds": ["%s"],
                  "dependencyIds": []
                }
                """.formatted(requirementId, architectureId);
        String invalidRequirementBody = updateBody.replace(
                "\"" + requirementId + "\"",
                "\"00000000-0000-0000-0000-000000000001\""
        );
        mockMvc.perform(put("/api/projects/{projectId}/development-tasks/{taskId}", projectId, taskId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequirementBody))
                .andExpect(status().isBadRequest());
        String invalidArchitectureBody = updateBody.replace(
                "\"" + architectureId + "\"",
                "\"00000000-0000-0000-0000-000000000002\""
        );
        mockMvc.perform(put("/api/projects/{projectId}/development-tasks/{taskId}", projectId, taskId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidArchitectureBody))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/projects/{projectId}/development-tasks/{taskId}", projectId, taskId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Implement the project API"));
        mockMvc.perform(get("/api/projects/{projectId}/development-tasks", projectId)
                        .param("priority", "HIGH")
                        .param("status", "TODO")
                        .param("milestone", "Milestone 1")
                        .session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post("/api/projects/{projectId}/development-tasks/{taskId}/approve", projectId, taskId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"));
        mockMvc.perform(put("/api/projects/{projectId}/development-tasks/{taskId}/status", projectId, taskId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mockMvc.perform(delete("/api/projects/{projectId}/development-tasks/{taskId}", projectId, taskId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsUnapprovedSourcesCyclesAndCrossOwnerAccess() throws Exception {
        AuthenticatedSession owner = register("planning.owner2", "planning.owner2@example.com");
        String projectId = createProject(owner);
        List<String> requirementIds = createPendingRequirements(owner, projectId);
        mockMvc.perform(post("/api/projects/{projectId}/development-tasks/generate", projectId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isConflict());

        requirementIds.forEach(requirementId -> {
            try {
                approveRequirement(owner, projectId, requirementId);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
        List<String> taskIds = generateTasks(owner, projectId);
        String firstId = taskIds.get(0);
        String secondId = taskIds.get(1);
        String requirementId = requirementIds.get(0);
        String cycleBody = """
                {
                  "title":"Cycle attempt",
                  "description":"Attempt to create a dependency cycle.",
                  "category":"API",
                  "priority":"HIGH",
                  "estimatedEffortHours":4,
                  "milestone":"Milestone 1",
                  "acceptanceCriteria":["No cycle is stored."],
                  "relatedRequirementIds":["%s"],
                  "architectureReferenceIds":[],
                  "dependencyIds":["%s"]
                }
                """.formatted(requirementId, secondId);
        mockMvc.perform(put("/api/projects/{projectId}/development-tasks/{taskId}", projectId, firstId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cycleBody))
                .andExpect(status().isBadRequest());

        AuthenticatedSession other = register("planning.other", "planning.other@example.com");
        mockMvc.perform(get("/api/projects/{projectId}/development-tasks/{taskId}", projectId, secondId)
                        .session(other.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects/{projectId}/development-tasks", projectId)
                        .session(other.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/projects/{projectId}/development-tasks/{taskId}/reject", projectId, secondId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REJECTED"));
        mockMvc.perform(post("/api/projects/{projectId}/development-tasks/{taskId}/approve", projectId, secondId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isConflict());
    }

    private List<String> generateTasks(AuthenticatedSession owner, String projectId) throws Exception {
        String body = mockMvc.perform(post("/api/projects/{projectId}/development-tasks/generate", projectId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        List<String> taskIds = new ArrayList<>();
        for (JsonNode task : objectMapper.readTree(body).get("tasks")) {
            taskIds.add(task.get("id").asText());
        }
        return taskIds;
    }

    private String createAndApproveArchitecture(AuthenticatedSession owner, String projectId, String requirementId) throws Exception {
        String body = mockMvc.perform(post("/api/projects/{projectId}/architecture/generate", projectId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceRequirementIds\":[\"" + requirementId + "\"]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String architectureId = objectMapper.readTree(body).get("id").asText();
        mockMvc.perform(post("/api/projects/{projectId}/architecture/{documentId}/approve", projectId, architectureId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isOk());
        return architectureId;
    }

    private String createAndApproveRequirement(AuthenticatedSession owner, String projectId) throws Exception {
        String id = createPendingRequirements(owner, projectId).get(0);
        approveRequirement(owner, projectId, id);
        return id;
    }

    private List<String> createPendingRequirements(AuthenticatedSession owner, String projectId) throws Exception {
        String body = mockMvc.perform(post("/api/projects/{projectId}/requirements/generate", projectId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        List<String> requirementIds = new ArrayList<>();
        for (JsonNode requirement : objectMapper.readTree(body).get("requirements")) {
            requirementIds.add(requirement.get("id").asText());
        }
        return requirementIds;
    }

    private void approveRequirement(AuthenticatedSession owner, String projectId, String requirementId) throws Exception {
        mockMvc.perform(post("/api/projects/{projectId}/requirements/{requirementId}/approve", projectId, requirementId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isOk());
    }

    private String createProject(AuthenticatedSession owner) throws Exception {
        String body = mockMvc.perform(post("/api/projects")
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Planning project",
                                  "description": "A project for planning workflow verification.",
                                  "objectives": "Deliver the approved project workflow.",
                                  "domain": "Software",
                                  "preferredStack": "React, Spring Boot, PostgreSQL",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private AuthenticatedSession register(String username, String email) throws Exception {
        CsrfDetails initialCsrf = getCsrf(null);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .cookie(initialCsrf.cookie())
                        .header("X-XSRF-TOKEN", initialCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s",
                                  "password": "%s",
                                  "firstName": "Test",
                                  "lastName": "Planner"
                                }
                                """.formatted(username, email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        CsrfDetails csrf = getCsrf(session);
        return new AuthenticatedSession(session, csrf.cookie(), csrf.token());
    }

    private CsrfDetails getCsrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) request.session(session);
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        return new CsrfDetails(cookie, body.get("token").asText());
    }

    private record AuthenticatedSession(MockHttpSession session, Cookie csrfCookie, String csrfToken) {
    }

    private record CsrfDetails(Cookie cookie, String token) {
    }
}

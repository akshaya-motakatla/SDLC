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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ArchitectureIntegrationTest {
    private static final String PASSWORD = "architecture-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RequirementRepository requirementRepository;

    @Autowired
    private ArchitectureDocumentRepository architectureDocumentRepository;

    @Autowired
    private DevelopmentTaskRepository developmentTaskRepository;

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
    void ownerCanGenerateEditReviewAndReopenVersionedArchitecture() throws Exception {
        AuthenticatedSession owner = register("architecture.owner", "architecture.owner@example.com");
        String projectId = createProject(owner, "Architecture workspace");
        String requirementId = createAndApproveRequirement(owner, projectId);

        String firstDocument = generate(owner, projectId, requirementId);
        JsonNode first = objectMapper.readTree(firstDocument);
        String firstId = first.get("id").asText();
        org.junit.jupiter.api.Assertions.assertEquals(1, first.get("version").asInt());
        org.junit.jupiter.api.Assertions.assertEquals("DRAFT", first.get("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals("MOCK_DEMO", first.get("provider").asText());
        org.junit.jupiter.api.Assertions.assertTrue(first.get("demo").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals(requirementId, first.get("sourceRequirementIds").get(0).asText());
        org.junit.jupiter.api.Assertions.assertEquals(4, first.get("diagrams").size());
        org.junit.jupiter.api.Assertions.assertTrue(first.get("content").asText().contains("REQ-0001"));

        mockMvc.perform(delete("/api/projects/{projectId}/requirements/{requirementId}", projectId, requirementId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isConflict());

        JsonNode invalidDiagrams = objectMapper.readTree(objectMapper.writeValueAsString(first.get("diagrams")));
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalidDiagrams)
                .put("components", "flowchart LR\n  this is not valid");
        String invalidUpdateBody = objectMapper.writeValueAsString(java.util.Map.of(
                "title", first.get("title").asText(),
                "architectureStyle", first.get("architectureStyle").asText(),
                "content", first.get("content").asText(),
                "diagrams", invalidDiagrams
        ));
        mockMvc.perform(put("/api/projects/{projectId}/architecture/{documentId}", projectId, firstId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidUpdateBody))
                .andExpect(status().isBadRequest());

        String updateBody = objectMapper.writeValueAsString(java.util.Map.of(
                "title", "Reviewed architecture",
                "architectureStyle", "Layered modular monolith",
                "content", first.get("content").asText().replace(
                        "A layered application",
                        "An owner-reviewed layered application"
                ),
                "diagrams", first.get("diagrams")
        ));
        mockMvc.perform(put("/api/projects/{projectId}/architecture/{documentId}", projectId, firstId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Reviewed architecture"));

        mockMvc.perform(post("/api/projects/{projectId}/architecture/{documentId}/approve", projectId, firstId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        String secondDocument = generate(owner, projectId, requirementId);
        JsonNode second = objectMapper.readTree(secondDocument);
        org.junit.jupiter.api.Assertions.assertEquals(2, second.get("version").asInt());
        org.junit.jupiter.api.Assertions.assertNotEquals(firstId, second.get("id").asText());

        mockMvc.perform(post(
                                "/api/projects/{projectId}/architecture/{documentId}/reject",
                                projectId,
                                second.get("id").asText()
                        )
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/projects/{projectId}/architecture", projectId).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[1].version").value(1));
        mockMvc.perform(get("/api/projects/{projectId}/architecture/{documentId}/versions", projectId, firstId)
                        .session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/projects/{projectId}/architecture/{documentId}", projectId, firstId)
                        .session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.title").value("Reviewed architecture"));

        mockMvc.perform(put("/api/projects/{projectId}/architecture/{documentId}", projectId, firstId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isConflict());
    }

    @Test
    void generationRejectsUnapprovedRequirementsAndOtherOwnersCannotAccessDocuments() throws Exception {
        AuthenticatedSession owner = register("architecture.owner2", "architecture.owner2@example.com");
        String projectId = createProject(owner, "Owned architecture workspace");
        String pendingRequirementId = createAndGetPendingRequirement(owner, projectId);

        mockMvc.perform(post("/api/projects/{projectId}/architecture/generate", projectId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceRequirementIds\":[\"" + pendingRequirementId + "\"]}"))
                .andExpect(status().isBadRequest());

        String approvedRequirementId = approveRequirement(owner, projectId, pendingRequirementId);
        String documentId = objectMapper.readTree(generate(owner, projectId, approvedRequirementId)).get("id").asText();

        AuthenticatedSession other = register("architecture.other", "architecture.other@example.com");
        mockMvc.perform(get("/api/projects/{projectId}/architecture", projectId).session(other.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects/{projectId}/architecture/{documentId}", projectId, documentId)
                        .session(other.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/projects/{projectId}/architecture/{documentId}/reject", projectId, documentId)
                        .session(other.session())
                        .cookie(other.csrfCookie())
                        .header("X-XSRF-TOKEN", other.csrfToken()))
                .andExpect(status().isNotFound());
    }

    private String createAndApproveRequirement(AuthenticatedSession owner, String projectId) throws Exception {
        String requirementId = createAndGetPendingRequirement(owner, projectId);
        return approveRequirement(owner, projectId, requirementId);
    }

    private String createAndGetPendingRequirement(AuthenticatedSession owner, String projectId) throws Exception {
        String body = mockMvc.perform(post("/api/projects/{projectId}/requirements/generate", projectId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("requirements").get(0).get("id").asText();
    }

    private String approveRequirement(AuthenticatedSession owner, String projectId, String requirementId) throws Exception {
        mockMvc.perform(post("/api/projects/{projectId}/requirements/{requirementId}/approve", projectId, requirementId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isOk());
        return requirementId;
    }

    private String generate(AuthenticatedSession owner, String projectId, String requirementId) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/architecture/generate", projectId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceRequirementIds\":[\"" + requirementId + "\"]}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String createProject(AuthenticatedSession owner, String name) throws Exception {
        String body = mockMvc.perform(post("/api/projects")
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "A project for architecture workflow verification.",
                                  "objectives": "Connect the design to approved project requirements.",
                                  "domain": "Software engineering",
                                  "preferredStack": "React, Spring Boot, PostgreSQL",
                                  "status": "ACTIVE"
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
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
                                  "lastName": "Architect"
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

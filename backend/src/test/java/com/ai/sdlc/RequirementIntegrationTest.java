package com.ai.sdlc;

import com.ai.sdlc.repository.ProjectRepository;
import com.ai.sdlc.repository.DevelopmentTaskRepository;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RequirementIntegrationTest {
    private static final String PASSWORD = "requirements-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RequirementRepository requirementRepository;

    @Autowired
    private DevelopmentTaskRepository developmentTaskRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void clearDatabase() {
        developmentTaskRepository.deleteAll();
        requirementRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerCanGenerateReviewAndManageRequirementsButOtherOwnersCannotAccessThem() throws Exception {
        mockMvc.perform(get("/api/projects/{projectId}/requirements", java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        AuthenticatedSession owner = register("requirements.owner", "requirements.owner@example.com");
        String projectBody = mockMvc.perform(post("/api/projects")
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Requirements workspace",
                                  "description": "A workspace for structured project requirements.",
                                  "objectives": "Support human-reviewed engineering work.",
                                  "domain": "Software",
                                  "preferredStack": "Java, React",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String projectId = objectMapper.readTree(projectBody).get("id").asText();

        String generationBody = mockMvc.perform(post("/api/projects/{projectId}/requirements/generate", projectId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("MOCK_DEMO"))
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.requirements.length()").value(2))
                .andExpect(jsonPath("$.requirements[0].status").value("PENDING"))
                .andExpect(jsonPath("$.requirements[0].acceptanceCriteria.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode generated = objectMapper.readTree(generationBody).get("requirements");
        String requirementId = generated.get(0).get("id").asText();

        mockMvc.perform(get("/api/projects/{projectId}/requirements", projectId).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/projects/{projectId}/requirements/{requirementId}", projectId, requirementId)
                        .session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requirementId));
        mockMvc.perform(put("/api/projects/{projectId}/requirements/{requirementId}", projectId, requirementId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Invalid requirement",
                                  "description": "Missing acceptance criteria.",
                                  "type": "FUNCTIONAL",
                                  "priority": "HIGH",
                                  "acceptanceCriteria": []
                                }
                                """))
                .andExpect(status().isBadRequest());

        AuthenticatedSession other = register("requirements.other", "requirements.other@example.com");
        mockMvc.perform(get("/api/projects/{projectId}/requirements", projectId).session(other.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects/{projectId}/requirements/{requirementId}", projectId, requirementId)
                        .session(other.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/projects/{projectId}/requirements/{requirementId}/approve", projectId, requirementId)
                        .session(other.session())
                        .cookie(other.csrfCookie())
                        .header("X-XSRF-TOKEN", other.csrfToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/projects/{projectId}/requirements/{requirementId}/approve", projectId, requirementId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(put("/api/projects/{projectId}/requirements/{requirementId}", projectId, requirementId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Edited requirement",
                                  "description": "Updated by the project owner.",
                                  "type": "FUNCTIONAL",
                                  "priority": "MEDIUM",
                                  "acceptanceCriteria": ["The edited content is returned.", "Review status is reset."]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Edited requirement"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(post("/api/projects/{projectId}/requirements/{requirementId}/reject", projectId, requirementId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(delete("/api/projects/{projectId}/requirements/{requirementId}", projectId, requirementId)
                        .session(owner.session())
                        .cookie(owner.csrfCookie())
                        .header("X-XSRF-TOKEN", owner.csrfToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/projects/{projectId}/requirements/{requirementId}", projectId, requirementId)
                        .session(owner.session()))
                .andExpect(status().isNotFound());
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
                                  "lastName": "Owner"
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
        if (session != null) {
            request.session(session);
        }
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

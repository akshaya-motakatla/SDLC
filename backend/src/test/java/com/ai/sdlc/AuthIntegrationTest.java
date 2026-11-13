package com.ai.sdlc;

import com.ai.sdlc.repository.ProjectRepository;
import com.ai.sdlc.repository.DevelopmentTaskRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {
    private static final String PASSWORD = "correct-horse-battery-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private DevelopmentTaskRepository developmentTaskRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void clearDatabase() {
        developmentTaskRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void projectEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registeredAccountsOwnOnlyTheirProjects() throws Exception {
        AuthenticatedSession firstAccount = register("first.user", "first@example.com");
        String projectResponse = mockMvc.perform(post("/api/projects")
                        .session(firstAccount.session())
                        .cookie(firstAccount.csrfCookie())
                        .header("X-XSRF-TOKEN", firstAccount.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Private workspace",
                                  "description": "First user's private project",
                                  "objectives": "Keep data isolated",
                                  "domain": "Engineering",
                                  "preferredStack": "Java, React",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Private workspace"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode project = objectMapper.readTree(projectResponse);
        String projectId = project.get("id").asText();

        mockMvc.perform(get("/api/projects").session(firstAccount.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/projects/{id}", projectId).session(firstAccount.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        AuthenticatedSession secondAccount = register("second.user", "second@example.com");
        mockMvc.perform(get("/api/projects").session(secondAccount.session()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        mockMvc.perform(get("/api/projects/{id}", projectId).session(secondAccount.session()))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/projects/{id}", projectId)
                        .session(firstAccount.session())
                        .cookie(firstAccount.csrfCookie())
                        .header("X-XSRF-TOKEN", firstAccount.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Private workspace",
                                  "description": "Updated project description",
                                  "objectives": "Keep data isolated",
                                  "domain": "Engineering",
                                  "preferredStack": "Java, React",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated project description"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        mockMvc.perform(delete("/api/projects/{id}", projectId)
                        .session(firstAccount.session())
                        .cookie(firstAccount.csrfCookie())
                        .header("X-XSRF-TOKEN", firstAccount.csrfToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/projects/{id}", projectId).session(firstAccount.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void loginRestoresTheRegisteredAccountSession() throws Exception {
        register("login.user", "login@example.com");
        CsrfDetails csrf = getCsrf(null);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"LOGIN@example.com","password":"correct-horse-battery-123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.com"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("login.user"));
    }

    @Test
    void registrationRequiresCsrfProtection() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "no.csrf",
                                  "email": "no-csrf@example.com",
                                  "password": "correct-horse-battery-123",
                                  "firstName": "No",
                                  "lastName": "Csrf"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutInvalidatesTheServerSideSession() throws Exception {
        AuthenticatedSession account = register("logout.user", "logout@example.com");

        mockMvc.perform(post("/api/auth/logout")
                        .session(account.session())
                        .cookie(account.csrfCookie())
                        .header("X-XSRF-TOKEN", account.csrfToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(account.session()))
                .andExpect(status().isUnauthorized());
    }

    private AuthenticatedSession register(String username, String email) throws Exception {
        CsrfDetails csrf = getCsrf(null);
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s",
                                  "password": "%s",
                                  "firstName": "Test",
                                  "lastName": "User"
                                }
                                """.formatted(username, email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();

        MockHttpSession session = (MockHttpSession) registerResult.getRequest().getSession(false);
        assertNotNull(session);
        CsrfDetails authenticatedCsrf = getCsrf(session);
        return new AuthenticatedSession(session, authenticatedCsrf.cookie(), authenticatedCsrf.token());
    }

    private CsrfDetails getCsrf(MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/auth/csrf");
        if (session != null) {
            request.session(session);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
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

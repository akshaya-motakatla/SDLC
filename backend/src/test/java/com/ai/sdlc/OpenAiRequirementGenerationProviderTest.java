package com.ai.sdlc;

import com.ai.sdlc.ai.OpenAiRequirementGenerationProvider;
import com.ai.sdlc.ai.RequirementContext;
import com.ai.sdlc.ai.RequirementGenerationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiRequirementGenerationProviderTest {
    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec request;

    @Mock
    private ChatClient.CallResponseSpec callResponse;

    private OpenAiRequirementGenerationProvider provider;

    @BeforeEach
    void setUp() {
        when(chatClient.prompt()).thenReturn(request);
        when(request.system(anyString())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(callResponse);
        provider = new OpenAiRequirementGenerationProvider(chatClient, new ObjectMapper());
    }

    @Test
    void identifiesRealProviderAndReturnsStructuredResponse() {
        String response = """
                {"requirements":[{"title":"Sign in","description":"Users sign in.","type":"FUNCTIONAL","priority":"HIGH","acceptanceCriteria":["A valid user reaches the workspace."]}]}
                """;
        when(callResponse.content()).thenReturn(response);

        assertEquals(response, provider.generate(new RequirementContext("Project", "Description", "Objectives", "Software", "Java")));
        assertEquals("OPENAI", provider.providerName());
        assertFalse(provider.isDemo());
    }

    @Test
    void translatesTimeoutsAndRateLimitsToSanitizedRetryableErrors() {
        when(callResponse.content()).thenThrow(new ResourceAccessException("Read timed out"));
        RequirementGenerationException timeout = assertThrows(
                RequirementGenerationException.class,
                () -> provider.generate(new RequirementContext("Project", "Description", null, "Software", "Java"))
        );
        assertEquals("The AI provider could not generate requirements. Please retry later.", timeout.getMessage());

        doThrow(new RuntimeException("429 rate limit")).when(callResponse).content();
        RequirementGenerationException rateLimit = assertThrows(
                RequirementGenerationException.class,
                () -> provider.generate(new RequirementContext("Project", "Description", null, "Software", "Java"))
        );
        assertEquals("The AI provider could not generate requirements. Please retry later.", rateLimit.getMessage());
    }
}

package com.ai.sdlc;

import com.ai.sdlc.ai.RequirementGenerationProvider;
import com.ai.sdlc.config.RequirementAiConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementAiConfigTest {
    private final RequirementAiConfig config = new RequirementAiConfig();

    @Test
    void mockProviderIsExplicitlyIdentifiedAsDemoOutput() {
        RequirementGenerationProvider provider = config.mockRequirementGenerationProvider();

        assertEquals("MOCK_DEMO", provider.providerName());
        assertTrue(provider.isDemo());
    }

    @Test
    void openAiProviderRefusesToStartWithoutAnApiKey() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> config.openAiRequirementGenerationProvider("", "gpt-4o-mini", new ObjectMapper())
        );

        assertEquals("APP_AI_PROVIDER=openai requires OPENAI_API_KEY to be set in the environment.", error.getMessage());
    }
}

package com.ai.sdlc.config;

import com.ai.sdlc.ai.MockRequirementGenerationProvider;
import com.ai.sdlc.ai.ArchitectureGenerationProvider;
import com.ai.sdlc.ai.MockArchitectureGenerationProvider;
import com.ai.sdlc.ai.OpenAiArchitectureGenerationProvider;
import com.ai.sdlc.ai.OpenAiRequirementGenerationProvider;
import com.ai.sdlc.ai.RequirementGenerationProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RequirementAiConfig {
    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
    public RequirementGenerationProvider mockRequirementGenerationProvider() {
        return new MockRequirementGenerationProvider();
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
    public ChatClient openAiChatClient(
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${OPENAI_MODEL:gpt-4o-mini}") String model
    ) {
        return createOpenAiChatClient(apiKey, model);
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
    public ArchitectureGenerationProvider mockArchitectureGenerationProvider(ObjectMapper objectMapper) {
        return new MockArchitectureGenerationProvider(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
    public RequirementGenerationProvider openAiRequirementGenerationProvider(ChatClient chatClient, ObjectMapper objectMapper) {
        return new OpenAiRequirementGenerationProvider(chatClient, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
    public ArchitectureGenerationProvider openAiArchitectureGenerationProvider(ChatClient chatClient, ObjectMapper objectMapper) {
        return new OpenAiArchitectureGenerationProvider(chatClient, objectMapper);
    }

    public RequirementGenerationProvider openAiRequirementGenerationProvider(String apiKey, String model, ObjectMapper objectMapper) {
        return new OpenAiRequirementGenerationProvider(createOpenAiChatClient(apiKey, model), objectMapper);
    }

    private ChatClient createOpenAiChatClient(String apiKey, String model) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("APP_AI_PROVIDER=openai requires OPENAI_API_KEY to be set in the environment.");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(45));
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(apiKey)
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(0.2)
                        .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, null))
                        .build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .build();
        return ChatClient.create(chatModel);
    }
}

package org.platform.nexus.llm;

import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.platform.nexus.config.LlmProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "gemini")
public class GeminiLlmClient implements LlmClient {

    private final LlmProperties.Provider config;
    private final RestClient restClient;

    public GeminiLlmClient(LlmProperties properties) {
        this.config = properties.gemini();
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .build();
    }

    @Override
    public String complete(String prompt) {
        JsonNode response = restClient.post()
                .uri("/models/{model}:generateContent", config.model())
                .header("x-goog-api-key", config.apiKey())
                .body(Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))))
                .retrieve()
                .body(JsonNode.class);
        if (response == null || response.path("candidates").isEmpty()) {
            throw new IllegalStateException("Gemini javobi bo'sh");
        }
        return response.path("candidates").get(0)
                .path("content").path("parts").get(0).path("text").asText();
    }

    @Override
    public String providerName() {
        return "gemini (" + config.model() + ")";
    }
}

package org.platform.nexus.llm;

import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.platform.nexus.config.LlmProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
public class OpenAiLlmClient implements LlmClient {

    private final LlmProperties.Provider config;
    private final RestClient restClient;

    public OpenAiLlmClient(LlmProperties properties) {
        this.config = properties.openai();
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .build();
    }

    @Override
    public String complete(String prompt) {
        JsonNode response = restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + config.apiKey())
                .body(Map.of(
                        "model", config.model(),
                        "messages", List.of(Map.of("role", "user", "content", prompt))))
                .retrieve()
                .body(JsonNode.class);
        if (response == null || response.path("choices").isEmpty()) {
            throw new IllegalStateException("OpenAI javobi bo'sh");
        }
        return response.path("choices").get(0).path("message").path("content").asText();
    }

    @Override
    public String providerName() {
        return "openai (" + config.model() + ")";
    }
}

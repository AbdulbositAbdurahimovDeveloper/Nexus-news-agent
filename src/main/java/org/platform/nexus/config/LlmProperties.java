package org.platform.nexus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(String provider, Provider anthropic, Provider openai, Provider gemini) {

    public record Provider(String apiKey, String model) {

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}

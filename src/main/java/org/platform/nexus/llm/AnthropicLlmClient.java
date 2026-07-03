package org.platform.nexus.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import java.util.stream.Collectors;
import org.platform.nexus.config.LlmProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "anthropic", matchIfMissing = true)
public class AnthropicLlmClient implements LlmClient {

    private final LlmProperties.Provider config;
    private volatile AnthropicClient client;

    public AnthropicLlmClient(LlmProperties properties) {
        this.config = properties.anthropic();
    }

    @Override
    public String complete(String prompt) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(config.model())
                .maxTokens(4096L)
                .addUserMessage(prompt)
                .build();
        Message response = client().messages().create(params);
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .collect(Collectors.joining("\n"));
    }

    @Override
    public String providerName() {
        return "anthropic (" + config.model() + ")";
    }

    // API kalit faqat birinchi chaqiruvda talab qilinadi — kalitisiz ham kontekst ko'tarila oladi
    private AnthropicClient client() {
        AnthropicClient local = client;
        if (local == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder().apiKey(config.apiKey()).build();
                }
                local = client;
            }
        }
        return local;
    }
}

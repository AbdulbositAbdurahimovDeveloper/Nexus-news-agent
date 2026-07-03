package org.platform.nexus.llm;

import java.util.List;
import java.util.Map;
import org.platform.nexus.config.LlmProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * Admin uchun LLM API holati — kalit kiritilgan BARCHA provayderlar tekshiriladi
 * (faqat faoli emas). Balans/billing'ni provayderlar API orqali bermaydi,
 * faqat rate-limit header'lari ko'rsatiladi.
 */
@Service
public class ApiStatusService {

    private final LlmProperties properties;
    private final RestClient restClient = RestClient.create();

    public ApiStatusService(LlmProperties properties) {
        this.properties = properties;
    }

    public String status() {
        String active = properties.provider();
        return """
                🔑 <b>API holati</b>
                ⭐ — hozir faol provayder

                %s

                %s

                %s

                💰 Balans/billing API orqali berilmaydi — provayder \
                konsolida ko'ring (platform.openai.com/usage, \
                console.anthropic.com, aistudio.google.com).""".formatted(
                section("anthropic", "Anthropic", properties.anthropic(), active),
                section("openai", "OpenAI", properties.openai(), active),
                section("gemini", "Gemini", properties.gemini(), active));
    }

    private String section(String key, String title, LlmProperties.Provider cfg, String active) {
        String header = "%s<b>%s</b> · <code>%s</code>\n".formatted(
                key.equals(active) ? "⭐ " : "▫️ ", title, cfg.model());
        if (!cfg.configured()) {
            return header + "🔒 API kalit kiritilmagan";
        }
        try {
            return header + switch (key) {
                case "openai" -> probeOpenAi(cfg);
                case "anthropic" -> probeAnthropic(cfg);
                case "gemini" -> probeGemini(cfg);
                default -> "❓";
            };
        } catch (RestClientResponseException e) {
            return header + errorHint(e);
        } catch (Exception e) {
            return header + "❌ Ulanish xatosi: " + e.getMessage();
        }
    }

    private String probeOpenAi(LlmProperties.Provider cfg) {
        ResponseEntity<JsonNode> response = restClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + cfg.apiKey())
                .body(Map.of(
                        "model", cfg.model(),
                        "max_tokens", 1,
                        "messages", List.of(Map.of("role", "user", "content", "ping"))))
                .retrieve()
                .toEntity(JsonNode.class);
        HttpHeaders h = response.getHeaders();
        return "✅ Ishlayapti\n📊 So'rovlar: %s/%s · Tokenlar: %s/%s (daqiqasiga)".formatted(
                header(h, "x-ratelimit-remaining-requests"), header(h, "x-ratelimit-limit-requests"),
                header(h, "x-ratelimit-remaining-tokens"), header(h, "x-ratelimit-limit-tokens"));
    }

    private String probeAnthropic(LlmProperties.Provider cfg) {
        // rate-limit header'lari SDK'da ochilmaydi — probe uchun to'g'ridan-to'g'ri so'rov
        ResponseEntity<JsonNode> response = restClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", cfg.apiKey())
                .header("anthropic-version", "2023-06-01")
                .body(Map.of(
                        "model", cfg.model(),
                        "max_tokens", 1,
                        "messages", List.of(Map.of("role", "user", "content", "ping"))))
                .retrieve()
                .toEntity(JsonNode.class);
        HttpHeaders h = response.getHeaders();
        return "✅ Ishlayapti\n📊 So'rovlar: %s/%s · Input tok: %s/%s".formatted(
                header(h, "anthropic-ratelimit-requests-remaining"),
                header(h, "anthropic-ratelimit-requests-limit"),
                header(h, "anthropic-ratelimit-input-tokens-remaining"),
                header(h, "anthropic-ratelimit-input-tokens-limit"));
    }

    private String probeGemini(LlmProperties.Provider cfg) {
        restClient.post()
                .uri("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
                        cfg.model())
                .header("x-goog-api-key", cfg.apiKey())
                .body(Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", "ping"))))))
                .retrieve()
                .toEntity(JsonNode.class);
        return "✅ Ishlayapti (Gemini rate-limit header bermaydi)";
    }

    private String errorHint(RestClientResponseException e) {
        return switch (e.getStatusCode().value()) {
            case 401, 403 -> "❌ HTTP %d — kalit noto'g'ri yoki bekor qilingan".formatted(
                    e.getStatusCode().value());
            case 429 -> "❌ HTTP 429 — kvota tugagan yoki free tier yo'q (billing yoqilganmi?)";
            case 404 -> "❌ HTTP 404 — model nomi noto'g'ri";
            default -> "❌ HTTP %d — kutilmagan javob".formatted(e.getStatusCode().value());
        };
    }

    private String header(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        return value == null ? "—" : value;
    }
}

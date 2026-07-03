package org.platform.nexus.telegram;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.platform.nexus.config.TelegramProperties;
import org.platform.nexus.telegram.dto.TelegramDtos.ApiResponse;
import org.platform.nexus.telegram.dto.TelegramDtos.Update;
import org.platform.nexus.telegram.dto.TelegramDtos.User;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class TelegramClient {

    /** Long polling so'rovi Telegram tomonida ushlab turiladigan vaqt (soniya). */
    static final int POLL_TIMEOUT_SECONDS = 25;

    private final RestClient restClient;

    public TelegramClient(TelegramProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        // read timeout long polling'dan uzun bo'lishi shart
        factory.setReadTimeout(Duration.ofSeconds(POLL_TIMEOUT_SECONDS + 10));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("https://api.telegram.org/bot" + properties.botToken())
                .build();
    }

    public List<Update> getUpdates(long offset) {
        ApiResponse<List<Update>> response = restClient.get()
                .uri(uri -> uri.path("/getUpdates")
                        .queryParam("offset", offset)
                        .queryParam("timeout", POLL_TIMEOUT_SECONDS)
                        .queryParam("allowed_updates", "[\"message\",\"callback_query\"]")
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return response != null && response.ok() && response.result() != null ? response.result() : List.of();
    }

    public void sendMessage(long chatId, String html, Map<String, Object> replyMarkup) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", html);
        body.put("parse_mode", "HTML");
        body.put("disable_web_page_preview", true);
        if (replyMarkup != null) {
            body.put("reply_markup", replyMarkup);
        }
        call("/sendMessage", body);
    }

    public void editMessageText(long chatId, long messageId, String html, Map<String, Object> replyMarkup) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("text", html);
        body.put("parse_mode", "HTML");
        body.put("disable_web_page_preview", true);
        if (replyMarkup != null) {
            body.put("reply_markup", replyMarkup);
        }
        call("/editMessageText", body);
    }

    public void answerCallbackQuery(String callbackQueryId) {
        call("/answerCallbackQuery", Map.of("callback_query_id", callbackQueryId));
    }

    /** Bot username'ini qaytaradi — token to'g'riligini tekshirish uchun. */
    public String getMe() {
        ApiResponse<User> response = restClient.get()
                .uri("/getMe")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return response != null && response.ok() && response.result() != null
                ? response.result().username()
                : null;
    }

    private void call(String method, Map<String, Object> body) {
        ApiResponse<Object> response = restClient.post()
                .uri(method)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        if (response != null && !response.ok()) {
            log.warn("Telegram {} xatosi: {}", method, response.description());
        }
    }
}

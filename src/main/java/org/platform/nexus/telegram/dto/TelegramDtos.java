package org.platform.nexus.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class TelegramDtos {

    private TelegramDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiResponse<T>(boolean ok, T result, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Update(
            @JsonProperty("update_id") long updateId,
            Message message,
            @JsonProperty("callback_query") CallbackQuery callbackQuery) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            @JsonProperty("message_id") long messageId,
            Chat chat,
            String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(long id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackQuery(String id, Message message, String data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(@JsonProperty("username") String username) {
    }

    public record UpdateList(List<Update> updates) {
    }
}

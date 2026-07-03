package org.platform.nexus.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param botToken       @BotFather token
 * @param allowedChatIds vergul bilan ajratilgan chat ID'lar ro'yxati (whitelist).
 *                       Bo'sh bo'lsa — faqat admin kira oladi.
 * @param adminChatId    bitta admin chat ID — statistika, API holati va
 *                       on-demand digest faqat unga ko'rinadi
 */
@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(String botToken, String allowedChatIds, String adminChatId) {

    public boolean configured() {
        return botToken != null && !botToken.isBlank();
    }

    public Set<Long> allowedChatIdSet() {
        if (allowedChatIds == null || allowedChatIds.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(allowedChatIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    public boolean isAdmin(long chatId) {
        return adminChatId != null && !adminChatId.isBlank()
                && Long.parseLong(adminChatId.trim()) == chatId;
    }

    public boolean isAllowed(long chatId) {
        return isAdmin(chatId) || allowedChatIdSet().contains(chatId);
    }
}

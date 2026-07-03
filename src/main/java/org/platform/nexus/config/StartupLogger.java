package org.platform.nexus.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.platform.nexus.llm.LlmClient;
import org.platform.nexus.telegram.TelegramClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Ishga tushganda konfiguratsiya diagnostikasini log'ga chiqaradi. */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupLogger implements ApplicationRunner {

    private final Environment environment;
    private final LlmProperties llmProperties;
    private final TelegramProperties telegramProperties;
    private final ObjectProvider<LlmClient> llmClientProvider;
    private final TelegramClient telegramClient;

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== news-agent konfiguratsiyasi ===");
        log.info("DB rejimi (profil): {}", String.join(",", environment.getActiveProfiles()));

        LlmClient llm = llmClientProvider.getIfAvailable();
        if (llm != null) {
            log.info("LLM_PROVIDER='{}' -> bean: {} [{}]",
                    llmProperties.provider(), llm.getClass().getSimpleName(), llm.providerName());
        } else {
            log.error("LLM_PROVIDER='{}' uchun bean YARATILMADI — anthropic|openai|gemini bo'lishi kerak",
                    llmProperties.provider());
        }

        if (telegramProperties.configured()) {
            try {
                String username = telegramClient.getMe();
                log.info("Telegram bot: @{}", username);
            } catch (Exception e) {
                log.error("Telegram token tekshiruvi muvaffaqiyatsiz: {}", e.getMessage());
            }
        } else {
            log.warn("TELEGRAM_BOT_TOKEN bo'sh — bot funksiyalari o'chirilgan");
        }
        log.info("===================================");
    }
}

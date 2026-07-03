package org.platform.nexus.telegram;

import lombok.extern.slf4j.Slf4j;
import org.platform.nexus.config.TelegramProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** Telegram getUpdates long polling sikli — alohida virtual thread'da ishlaydi. */
@Slf4j
@Component
public class LongPollingRunner implements SmartLifecycle {

    private final TelegramProperties properties;
    private final TelegramClient telegram;
    private final BotHandler handler;

    private volatile boolean running;
    private Thread pollingThread;

    public LongPollingRunner(TelegramProperties properties, TelegramClient telegram, BotHandler handler) {
        this.properties = properties;
        this.telegram = telegram;
        this.handler = handler;
    }

    @Override
    public void start() {
        if (!properties.configured()) {
            log.warn("TELEGRAM_BOT_TOKEN berilmagan — bot long polling ishga tushirilmadi");
            return;
        }
        running = true;
        pollingThread = Thread.ofVirtual().name("telegram-polling").start(this::pollLoop);
        log.info("Telegram long polling boshlandi");
    }

    private void pollLoop() {
        long offset = 0;
        while (running) {
            try {
                var updates = telegram.getUpdates(offset);
                for (var update : updates) {
                    offset = update.updateId() + 1;
                    handler.handle(update);
                }
            } catch (Exception e) {
                log.warn("Polling xatosi ({}) — 3s dan keyin qayta urinaman", e.getMessage());
                sleep(3000);
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        running = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}

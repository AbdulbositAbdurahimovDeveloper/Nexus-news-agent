package org.platform.nexus.telegram;

import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.platform.nexus.category.NewsCategory;
import org.platform.nexus.subscription.Subscription;
import org.platform.nexus.subscription.SubscriptionService;
import org.platform.nexus.telegram.dto.TelegramDtos.CallbackQuery;
import org.platform.nexus.telegram.dto.TelegramDtos.Message;
import org.platform.nexus.telegram.dto.TelegramDtos.Update;
import org.springframework.stereotype.Component;

/** Kelgan update'larni komanda va callback'larga taqsimlaydi. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotHandler {

    private final TelegramClient telegram;
    private final SubscriptionService subscriptions;
    private final org.platform.nexus.config.TelegramProperties properties;
    private final org.platform.nexus.scheduler.DigestService digestService;
    private final org.platform.nexus.llm.ApiStatusService apiStatusService;
    private final org.platform.nexus.digest.DigestRepository digestRepository;

    public void handle(Update update) {
        try {
            if (update.message() != null && update.message().text() != null) {
                if (!authorize(update.message().chat().id())) {
                    return;
                }
                handleCommand(update.message());
            } else if (update.callbackQuery() != null) {
                if (update.callbackQuery().message() != null
                        && !properties.isAllowed(update.callbackQuery().message().chat().id())) {
                    telegram.answerCallbackQuery(update.callbackQuery().id());
                    return;
                }
                handleCallback(update.callbackQuery());
            }
        } catch (Exception e) {
            log.error("Update qayta ishlashda xato: {}", e.getMessage(), e);
        }
    }

    /** Whitelist tekshiruvi — ruxsatsiz foydalanuvchiga o'z chat ID'sini aytadi. */
    private boolean authorize(long chatId) {
        if (properties.isAllowed(chatId)) {
            return true;
        }
        log.info("Ruxsatsiz kirish urinishi: chat_id={}", chatId);
        telegram.sendMessage(chatId, Texts.unauthorized(chatId), null);
        return false;
    }

    private void handleCommand(Message message) {
        long chatId = message.chat().id();
        String command = message.text().trim().split("\\s+")[0];
        boolean admin = properties.isAdmin(chatId);
        switch (command) {
            case "/start", "/menu" ->
                    telegram.sendMessage(chatId, Texts.welcome(), Keyboards.mainMenu(admin));
            case "/list" -> sendList(chatId);
            case "/help" -> telegram.sendMessage(chatId, Texts.help(), Keyboards.backToMenu());
            default -> telegram.sendMessage(chatId,
                    "Tushunmadim 🤔 /menu buyrug'idan foydalaning.", Keyboards.backToMenu());
        }
    }

    private void handleCallback(CallbackQuery callback) {
        telegram.answerCallbackQuery(callback.id());
        if (callback.message() == null || callback.data() == null) {
            return;
        }
        long chatId = callback.message().chat().id();
        long messageId = callback.message().messageId();
        String[] parts = callback.data().split(":");

        switch (parts[0]) {
            case "menu" -> handleMenu(parts[1], chatId, messageId);
            case "cat" -> openTimeGrid(chatId, messageId, NewsCategory.valueOf(parts[1]));
            case "time" -> handleTimeToggle(chatId, messageId, parts);
            case "freq" -> {
                NewsCategory category = NewsCategory.valueOf(parts[1]);
                int mask = Integer.parseInt(parts[2], 16);
                telegram.editMessageText(chatId, messageId,
                        Texts.chooseFrequency(category), Keyboards.frequency(category, mask));
            }
            case "freq-set" -> handleSave(chatId, messageId, parts);
            case "sub" -> handleSubscriptionAction(chatId, messageId, parts);
            case "admin" -> handleAdmin(parts[1], chatId, messageId);
            case "now" -> handleDeliverNow(chatId, messageId, parts[1]);
            case "pg" -> handleDigestPage(chatId, messageId, parts);
            case "noop" -> { /* sahifa ko'rsatkichi tugmasi — hech narsa qilinmaydi */ }
            default -> log.warn("Noma'lum callback: {}", callback.data());
        }
    }

    private void handleMenu(String action, long chatId, long messageId) {
        switch (action) {
            case "add" -> telegram.editMessageText(chatId, messageId,
                    Texts.chooseCategory(), Keyboards.categories());
            case "list" -> editList(chatId, messageId);
            case "help" -> telegram.editMessageText(chatId, messageId, Texts.help(), Keyboards.backToMenu());
            case "home" -> telegram.editMessageText(chatId, messageId,
                    Texts.welcome(), Keyboards.mainMenu(properties.isAdmin(chatId)));
            default -> log.warn("Noma'lum menu action: {}", action);
        }
    }

    private void handleAdmin(String action, long chatId, long messageId) {
        if (!properties.isAdmin(chatId)) {
            log.warn("Admin bo'lmagan foydalanuvchi admin callback yubordi: {}", chatId);
            return;
        }
        switch (action) {
            case "now" -> telegram.editMessageText(chatId, messageId,
                    "📬 Qaysi kategoriya bo'yicha hozir yuboray?", Keyboards.categoriesNow());
            case "stats" -> telegram.editMessageText(chatId, messageId,
                    Texts.adminStats(subscriptions.listAll()), Keyboards.backToMenu());
            case "api" -> {
                telegram.editMessageText(chatId, messageId, "⏳ API tekshirilmoqda...", null);
                // probe 1-3s oladi — polling oqimini bloklamaslik uchun alohida thread
                Thread.ofVirtual().start(() ->
                        telegram.sendMessage(chatId, apiStatusService.status(), Keyboards.backToMenu()));
            }
            default -> log.warn("Noma'lum admin action: {}", action);
        }
    }

    private void handleDeliverNow(long chatId, long messageId, String categoryName) {
        if (!properties.isAdmin(chatId)) {
            return;
        }
        NewsCategory category = NewsCategory.valueOf(categoryName);
        telegram.editMessageText(chatId, messageId,
                "⏳ %s bo'yicha digest tayyorlanmoqda...".formatted(category.label()), null);
        // RSS + LLM bir necha soniya oladi — pollingni bloklamaymiz
        Thread.ofVirtual().start(() -> {
            try {
                digestService.deliverNow(chatId, category);
            } catch (Exception e) {
                log.error("On-demand digest xatosi: {}", e.getMessage(), e);
                telegram.sendMessage(chatId, "❌ Xato: " + Texts.escapeHtml(e.getMessage()), null);
            }
        });
    }

    private void openTimeGrid(long chatId, long messageId, NewsCategory category) {
        // mavjud obuna bo'lsa vaqtlari oldindan belgilangan holda ochiladi
        int mask = subscriptions.find(chatId, category)
                .map(sub -> Keyboards.maskFromSendTimes(sub.sendTimesList()))
                .orElse(0);
        telegram.editMessageText(chatId, messageId,
                Texts.chooseTimes(category, Integer.bitCount(mask)), Keyboards.timeGrid(category, mask));
    }

    private void handleTimeToggle(long chatId, long messageId, String[] parts) {
        NewsCategory category = NewsCategory.valueOf(parts[1]);
        int mask = Integer.parseInt(parts[2], 16);
        if (!"-".equals(parts[3])) {
            int hour = Integer.parseInt(parts[3]);
            mask ^= 1 << (hour - Keyboards.FIRST_HOUR);
        }
        telegram.editMessageText(chatId, messageId,
                Texts.chooseTimes(category, Integer.bitCount(mask)), Keyboards.timeGrid(category, mask));
    }

    private void handleSave(long chatId, long messageId, String[] parts) {
        NewsCategory category = NewsCategory.valueOf(parts[1]);
        int mask = Integer.parseInt(parts[2], 16);
        int intervalDays = Integer.parseInt(parts[3]);
        Set<Integer> hours = new TreeSet<>();
        for (int hour = Keyboards.FIRST_HOUR; hour <= Keyboards.LAST_HOUR; hour++) {
            if ((mask & (1 << (hour - Keyboards.FIRST_HOUR))) != 0) {
                hours.add(hour);
            }
        }
        Subscription saved = subscriptions.upsert(chatId, category, hours, intervalDays);
        telegram.editMessageText(chatId, messageId, Texts.saved(saved), Keyboards.backToMenu());
    }

    private void handleSubscriptionAction(long chatId, long messageId, String[] parts) {
        long id = Long.parseLong(parts[2]);
        switch (parts[1]) {
            case "del" -> {
                subscriptions.delete(id);
                editList(chatId, messageId);
            }
            case "pause" -> {
                subscriptions.togglePause(id);
                editList(chatId, messageId);
            }
            case "edit" -> subscriptions.find(id).ifPresent(sub ->
                    openTimeGrid(chatId, messageId, sub.getCategory()));
            default -> log.warn("Noma'lum sub action: {}", parts[1]);
        }
    }

    private void handleDigestPage(long chatId, long messageId, String[] parts) {
        long digestId = Long.parseLong(parts[1]);
        int page = Integer.parseInt(parts[2]);
        digestRepository.findById(digestId).ifPresentOrElse(digest -> {
            if (digest.getChatId() != chatId || page < 0 || page >= digest.getPageCount()) {
                return;
            }
            telegram.editMessageText(chatId, messageId, digest.page(page),
                    Keyboards.pager(digestId, page, digest.getPageCount()));
        }, () -> telegram.editMessageText(chatId, messageId,
                "⌛️ Bu digest eskirgan (7 kundan keyin tozalanadi).", null));
    }

    private void sendList(long chatId) {
        var list = subscriptions.listByChat(chatId);
        telegram.sendMessage(chatId, Texts.subscriptionList(list), Keyboards.subscriptionList(list));
    }

    private void editList(long chatId, long messageId) {
        var list = subscriptions.listByChat(chatId);
        telegram.editMessageText(chatId, messageId, Texts.subscriptionList(list), Keyboards.subscriptionList(list));
    }
}

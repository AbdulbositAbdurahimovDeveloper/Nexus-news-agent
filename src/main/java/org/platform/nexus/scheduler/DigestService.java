package org.platform.nexus.scheduler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.platform.nexus.category.NewsCategory;
import org.platform.nexus.config.NewsProperties;
import org.platform.nexus.digest.Digest;
import org.platform.nexus.digest.DigestRepository;
import org.platform.nexus.feed.Article;
import org.platform.nexus.feed.RssFetcher;
import org.platform.nexus.llm.LlmClient;
import org.platform.nexus.sent.SentArticle;
import org.platform.nexus.sent.SentArticleRepository;
import org.platform.nexus.subscription.Subscription;
import org.platform.nexus.subscription.SubscriptionService;
import org.platform.nexus.telegram.Keyboards;
import org.platform.nexus.telegram.TelegramClient;
import org.platform.nexus.telegram.Texts;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Bitta obuna uchun digest: fetch → dedup → LLM (tartiblash + tarjima + xulosa)
 * → sahifalab yuborish → bazaga yozish.
 */
@Slf4j
@Service
public class DigestService {

    /** Maksimum 3 sahifa × 5 maqola. */
    private static final int MAX_ARTICLES = 15;
    private static final int MAX_PER_PAGE = 5;
    private static final DateTimeFormatter HEADER_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final RssFetcher rssFetcher;
    private final ObjectProvider<LlmClient> llmClientProvider;
    private final TelegramClient telegram;
    private final SentArticleRepository sentArticles;
    private final SubscriptionService subscriptions;
    private final DigestRepository digests;
    private final ZoneId zone;

    public DigestService(RssFetcher rssFetcher,
                         ObjectProvider<LlmClient> llmClientProvider,
                         TelegramClient telegram,
                         SentArticleRepository sentArticles,
                         SubscriptionService subscriptions,
                         DigestRepository digests,
                         NewsProperties newsProperties) {
        this.rssFetcher = rssFetcher;
        this.llmClientProvider = llmClientProvider;
        this.telegram = telegram;
        this.sentArticles = sentArticles;
        this.subscriptions = subscriptions;
        this.digests = digests;
        this.zone = ZoneId.of(newsProperties.timezone());
    }

    /** Tayyor element: o'zbekcha sarlavha + batafsil xulosa (ahamiyat tartibida). */
    private record DigestItem(Article article, String title, String summary) {
    }

    public void deliver(Subscription subscription) {
        sendDigest(subscription.getChatId(), subscription.getCategory(), false);
        subscriptions.markSent(subscription, LocalDate.now(zone));
    }

    /** Admin "hozir yuborish" — jadvaldan qat'i nazar darhol digest (dedup saqlanadi). */
    public void deliverNow(long chatId, NewsCategory category) {
        sendDigest(chatId, category, true);
    }

    private void sendDigest(long chatId, NewsCategory category, boolean notifyIfEmpty) {
        List<Article> fresh = rssFetcher.fetch(category).stream()
                .filter(article -> !sentArticles.existsByChatIdAndUrlHash(chatId, sha256(article.url())))
                .limit(MAX_ARTICLES)
                .toList();

        if (fresh.isEmpty()) {
            log.info("Yangi maqola yo'q: chat={} category={}", chatId, category);
            if (notifyIfEmpty) {
                telegram.sendMessage(chatId,
                        "📭 %s bo'yicha yangi (hali yuborilmagan) maqola topilmadi."
                                .formatted(category.label()), null);
            }
            return;
        }

        List<DigestItem> items = rankAndSummarize(category, fresh);
        List<String> pages = buildPages(category, items);
        if (pages.size() == 1) {
            telegram.sendMessage(chatId, pages.get(0), null);
        } else {
            // bitta xabar + sahifa tugmalari; sahifalar bazada (restart'ga chidamli)
            Digest digest = new Digest();
            digest.setChatId(chatId);
            digest.setPages(pages);
            digests.save(digest);
            digests.deleteByCreatedAtBefore(LocalDateTime.now(zone).minusDays(7));
            telegram.sendMessage(chatId, pages.get(0),
                    Keyboards.pager(digest.getId(), 0, pages.size()));
        }

        for (Article article : fresh) {
            SentArticle sent = new SentArticle();
            sent.setChatId(chatId);
            sent.setCategory(category.name());
            sent.setUrlHash(sha256(article.url()));
            sent.setTitle(truncate(article.title(), 500));
            sentArticles.save(sent);
        }
        log.info("Digest yuborildi: chat={} category={} maqolalar={}", chatId, category, fresh.size());
    }

    /**
     * LLM: ahamiyat bo'yicha tartiblaydi, sarlavhani o'zbekchaga tarjima qiladi,
     * 2-4 gaplik xulosa yozadi. Xato bo'lsa — asl tartib va description bilan fallback.
     */
    private List<DigestItem> rankAndSummarize(NewsCategory category, List<Article> articles) {
        LlmClient llm = llmClientProvider.getIfAvailable();
        if (llm == null) {
            log.error("LLM provayder bean topilmadi (LLM_PROVIDER tekshiring) — fallback rejim");
            return fallbackItems(articles);
        }
        try {
            String response = llm.complete(buildPrompt(category, articles));
            List<DigestItem> parsed = parseResponse(response, articles);
            return parsed.isEmpty() ? fallbackItems(articles) : parsed;
        } catch (Exception e) {
            log.error("LLM xatosi ({}) — fallback rejim: {}", llm.providerName(), e.getMessage());
            return fallbackItems(articles);
        }
    }

    private String buildPrompt(NewsCategory category, List<Article> articles) {
        StringBuilder sb = new StringBuilder("""
                Quyida "%s" kategoriyasidagi %d ta yangilik. Vazifang:

                1. Yangiliklarni keng auditoriya uchun ommaboplik va ahamiyati bo'yicha \
                tartibla — eng muhimi va qiziqarlisi BIRINCHI bo'lsin.
                2. Har bir yangilik sarlavhasini o'zbek tiliga tarjima qil (tabiiy, ravon).
                3. Har biriga o'zbek tilida 2-4 gaplik ANIQ va TUSHUNARLI xulosa yoz: \
                nima bo'ldi, kim ishtirok etdi, nima uchun bu muhim yoki qiziqarli. \
                Umumiy gaplar emas, konkret faktlar yoz.
                4. Xulosadagi eng muhim 1-3 ta so'z yoki iborani *yulduzcha* ichiga ol \
                (masalan: *Apple*, *40%% o'sish*, *birinchi marta*) — ular qalin \
                ko'rsatiladi. Yulduzchani boshqa maqsadda ishlatma.

                Javobni FAQAT quyidagi formatda qaytar (har qator bitta yangilik, \
                ahamiyat tartibida):
                <maqola raqami>|<o'zbekcha sarlavha>|<batafsil xulosa>

                Boshqa hech qanday matn yozma.

                Yangiliklar:
                """.formatted(category.displayName(), articles.size()));
        for (int i = 0; i < articles.size(); i++) {
            Article a = articles.get(i);
            sb.append("\n%d. %s\n   Manba: %s\n   Tavsif: %s\n".formatted(
                    i + 1, a.title(), a.source(), truncate(a.description(), 300)));
        }
        return sb.toString();
    }

    private List<DigestItem> parseResponse(String response, List<Article> articles) {
        List<DigestItem> result = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (String line : response.split("\n")) {
            String[] parts = line.split("\\|", 3);
            if (parts.length < 3) {
                continue;
            }
            try {
                int index = Integer.parseInt(parts[0].trim().replaceAll("\\D", ""));
                if (index >= 1 && index <= articles.size() && used.add(index)) {
                    result.add(new DigestItem(
                            articles.get(index - 1), parts[1].trim(), parts[2].trim()));
                }
            } catch (NumberFormatException ignored) {
                // format buzilgan qator — tashlab ketiladi
            }
        }
        // LLM tashlab ketgan maqolalar oxiriga qo'shiladi
        for (int i = 0; i < articles.size(); i++) {
            if (!used.contains(i + 1)) {
                Article a = articles.get(i);
                result.add(new DigestItem(a, a.title(), truncate(a.description(), 250)));
            }
        }
        return result;
    }

    private List<DigestItem> fallbackItems(List<Article> articles) {
        return articles.stream()
                .map(a -> new DigestItem(a, a.title(), truncate(a.description(), 250)))
                .toList();
    }

    private static final String DIVIDER = "➖➖➖➖➖➖➖➖➖➖";
    private static final String[] DIGIT_EMOJI =
            {"0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣"};
    private static final DateTimeFormatter ITEM_TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** Elementlarni 3-5 talik sahifalarga teng taqsimlab HTML xabarlar quradi. */
    private List<String> buildPages(NewsCategory category, List<DigestItem> items) {
        int total = items.size();
        int pageCount = (total + MAX_PER_PAGE - 1) / MAX_PER_PAGE;
        String date = LocalDateTime.now(zone).format(HEADER_TIME);

        List<String> pages = new ArrayList<>();
        int position = 0;
        int number = 1;
        for (int page = 0; page < pageCount; page++) {
            // qoldiqni teng taqsimlash: 7 ta -> 4+3 (5+2 emas)
            int size = (total - position + (pageCount - page - 1)) / (pageCount - page);
            StringBuilder sb = new StringBuilder();
            sb.append("📰 <b>%s YANGILIKLARI</b>\n".formatted(category.label().toUpperCase()));
            sb.append("🗓 %s%s\n%s\n".formatted(
                    date,
                    pageCount > 1 ? " · 📄 %d/%d".formatted(page + 1, pageCount) : "",
                    DIVIDER));
            for (int i = 0; i < size; i++, position++, number++) {
                DigestItem item = items.get(position);
                sb.append("\n%s <b>%s</b>\n".formatted(
                        numberEmoji(number), Texts.escapeHtml(item.title())));
                if (!item.summary().isBlank()) {
                    sb.append('\n').append(boldify(Texts.escapeHtml(item.summary()))).append('\n');
                }
                sb.append("\n🔗 <a href=\"%s\">%s</a> · 🕐 %s\n%s\n".formatted(
                        item.article().url(),
                        Texts.escapeHtml(item.article().source()),
                        LocalDateTime.ofInstant(item.article().publishedAt(), zone).format(ITEM_TIME),
                        DIVIDER));
            }
            pages.add(sb.toString());
        }
        return pages;
    }

    private String numberEmoji(int number) {
        if (number == 10) {
            return "🔟";
        }
        StringBuilder sb = new StringBuilder();
        for (char digit : String.valueOf(number).toCharArray()) {
            sb.append(DIGIT_EMOJI[digit - '0']);
        }
        return sb.toString();
    }

    /** LLM *yulduzcha* bilan belgilagan iboralarni qalinga o'giradi (escape'dan keyin). */
    private String boldify(String escapedText) {
        return escapedText.replaceAll("\\*([^*\\n]{1,80}?)\\*", "<b>$1</b>");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 mavjud emas", e);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}

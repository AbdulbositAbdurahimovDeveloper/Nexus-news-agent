package org.platform.nexus.scheduler;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.platform.nexus.config.NewsProperties;
import org.platform.nexus.config.TelegramProperties;
import org.platform.nexus.subscription.Subscription;
import org.platform.nexus.subscription.SubscriptionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Har daqiqa "vaqti kelgan" obunalarni tekshiruvchi poller.
 * Holat to'liq bazada saqlanadi — restart'ga chidamli.
 */
@Slf4j
@Component
public class DeliveryScheduler {

    private final SubscriptionService subscriptions;
    private final DigestService digestService;
    private final TelegramProperties telegramProperties;
    private final ZoneId zone;

    public DeliveryScheduler(SubscriptionService subscriptions,
                             DigestService digestService,
                             TelegramProperties telegramProperties,
                             NewsProperties newsProperties) {
        this.subscriptions = subscriptions;
        this.digestService = digestService;
        this.telegramProperties = telegramProperties;
        this.zone = ZoneId.of(newsProperties.timezone());
    }

    @Scheduled(cron = "0 * * * * *", zone = "${news.timezone}")
    public void tick() {
        LocalDate today = LocalDate.now(zone);
        LocalTime now = LocalTime.now(zone).truncatedTo(ChronoUnit.MINUTES);
        List<Subscription> due = subscriptions.findDue(today, now);
        if (due.isEmpty()) {
            return;
        }
        log.info("Scheduler tick {}: {} ta obuna navbatda", now, due.size());
        for (Subscription subscription : due) {
            // ro'yxatdan chiqarilgan foydalanuvchiga yuborilmaydi
            if (!telegramProperties.isAllowed(subscription.getChatId())) {
                log.info("Whitelist'da yo'q, o'tkazib yuborildi: chat={}", subscription.getChatId());
                continue;
            }
            try {
                digestService.deliver(subscription);
            } catch (Exception e) {
                // bitta obuna yiqilsa qolganlariga ta'sir qilmaydi
                log.error("Digest yuborishda xato: chat={} category={} — {}",
                        subscription.getChatId(), subscription.getCategory(), e.getMessage(), e);
            }
        }
    }
}

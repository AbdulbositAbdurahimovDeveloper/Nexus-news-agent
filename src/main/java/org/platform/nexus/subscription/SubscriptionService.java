package org.platform.nexus.subscription;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.platform.nexus.category.NewsCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final SubscriptionRepository repository;

    @Transactional
    public Subscription upsert(long chatId, NewsCategory category, Set<Integer> hours, int intervalDays) {
        String sendTimes = hours.stream()
                .sorted()
                .map(h -> String.format("%02d:00", h))
                .collect(Collectors.joining(","));
        Subscription subscription = repository.findByChatIdAndCategory(chatId, category)
                .orElseGet(Subscription::new);
        subscription.setChatId(chatId);
        subscription.setCategory(category);
        subscription.setSendTimes(sendTimes);
        subscription.setIntervalDays(intervalDays);
        subscription.setEnabled(true);
        return repository.save(subscription);
    }

    @Transactional(readOnly = true)
    public List<Subscription> listAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Subscription> listByChat(long chatId) {
        return repository.findByChatIdOrderByCategory(chatId);
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> find(long id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> find(long chatId, NewsCategory category) {
        return repository.findByChatIdAndCategory(chatId, category);
    }

    @Transactional
    public void delete(long id) {
        repository.deleteById(id);
    }

    @Transactional
    public Optional<Subscription> togglePause(long id) {
        return repository.findById(id).map(sub -> {
            sub.setEnabled(!sub.isEnabled());
            return repository.save(sub);
        });
    }

    /** Hozirgi daqiqada yuborilishi kerak bo'lgan obunalar. */
    @Transactional(readOnly = true)
    public List<Subscription> findDue(LocalDate today, LocalTime now) {
        String currentTime = now.format(HH_MM);
        return repository.findByEnabledTrue().stream()
                .filter(sub -> sub.sendTimesList().contains(currentTime))
                .filter(sub -> intervalElapsed(sub, today))
                .toList();
    }

    @Transactional
    public void markSent(Subscription subscription, LocalDate date) {
        subscription.setLastSentDate(date);
        repository.save(subscription);
    }

    private boolean intervalElapsed(Subscription sub, LocalDate today) {
        LocalDate last = sub.getLastSentDate();
        return last == null || !last.plusDays(sub.getIntervalDays()).isAfter(today);
    }
}

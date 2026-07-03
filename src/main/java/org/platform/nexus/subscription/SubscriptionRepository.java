package org.platform.nexus.subscription;

import java.util.List;
import java.util.Optional;
import org.platform.nexus.category.NewsCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByChatIdOrderByCategory(long chatId);

    Optional<Subscription> findByChatIdAndCategory(long chatId, NewsCategory category);

    List<Subscription> findByEnabledTrue();
}

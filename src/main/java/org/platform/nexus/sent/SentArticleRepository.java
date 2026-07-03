package org.platform.nexus.sent;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SentArticleRepository extends JpaRepository<SentArticle, Long> {

    boolean existsByChatIdAndUrlHash(long chatId, String urlHash);
}

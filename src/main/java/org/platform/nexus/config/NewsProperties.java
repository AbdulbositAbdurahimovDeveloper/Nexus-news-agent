package org.platform.nexus.config;

import java.util.List;
import java.util.Map;
import org.platform.nexus.category.NewsCategory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "news")
public record NewsProperties(String timezone, Map<NewsCategory, CategoryFeeds> categories) {

    public record CategoryFeeds(List<String> feeds) {
    }

    public List<String> feedsOf(NewsCategory category) {
        CategoryFeeds cf = categories == null ? null : categories.get(category);
        return cf == null || cf.feeds() == null ? List.of() : cf.feeds();
    }
}

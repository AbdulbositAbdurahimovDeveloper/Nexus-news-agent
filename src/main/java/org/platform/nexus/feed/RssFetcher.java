package org.platform.nexus.feed;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.platform.nexus.category.NewsCategory;
import org.platform.nexus.config.NewsProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RssFetcher {

    private static final Duration FEED_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration FRESHNESS_WINDOW = Duration.ofHours(24);

    private final NewsProperties properties;
    private final HttpClient httpClient;

    public RssFetcher(NewsProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(FEED_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Kategoriya feed'laridan oxirgi 24 soat ichidagi maqolalarni yig'adi. */
    public List<Article> fetch(NewsCategory category) {
        Instant cutoff = Instant.now().minus(FRESHNESS_WINDOW);
        List<Article> articles = new ArrayList<>();
        for (String feedUrl : properties.feedsOf(category)) {
            try {
                articles.addAll(readFeed(feedUrl, cutoff));
            } catch (Exception e) {
                log.warn("Feed o'qib bo'lmadi [{}]: {}", feedUrl, e.getMessage());
            }
        }
        articles.sort(Comparator.comparing(Article::publishedAt).reversed());
        return articles;
    }

    private List<Article> readFeed(String feedUrl, Instant cutoff) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(feedUrl))
                .timeout(FEED_TIMEOUT)
                .header("User-Agent", "news-agent/1.0 (+https://github.com)")
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        try (InputStream body = response.body()) {
            SyndFeed feed = new SyndFeedInput().build(new XmlReader(body));
            String source = feed.getTitle() != null ? feed.getTitle().trim() : URI.create(feedUrl).getHost();
            List<Article> result = new ArrayList<>();
            for (SyndEntry entry : feed.getEntries()) {
                Instant published = publishedAt(entry);
                if (published == null || published.isBefore(cutoff)) {
                    continue;
                }
                if (entry.getLink() == null || entry.getTitle() == null) {
                    continue;
                }
                result.add(new Article(
                        entry.getTitle().trim(),
                        entry.getLink().trim(),
                        source,
                        published,
                        entry.getDescription() != null ? plainText(entry.getDescription().getValue()) : ""));
            }
            return result;
        }
    }

    private Instant publishedAt(SyndEntry entry) {
        Date date = entry.getPublishedDate() != null ? entry.getPublishedDate() : entry.getUpdatedDate();
        return date == null ? null : date.toInstant();
    }

    private String plainText(String html) {
        if (html == null) {
            return "";
        }
        String text = html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return text.length() > 400 ? text.substring(0, 400) : text;
    }
}

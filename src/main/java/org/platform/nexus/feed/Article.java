package org.platform.nexus.feed;

import java.time.Instant;

public record Article(String title, String url, String source, Instant publishedAt, String description) {
}

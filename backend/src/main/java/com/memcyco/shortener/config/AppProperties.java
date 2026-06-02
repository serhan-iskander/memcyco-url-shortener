package com.memcyco.shortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String baseUrl,
        Cache cache,
        ClickTracker clickTracker,
        RateLimit rateLimit,
        Geo geo
) {
    public record Cache(long shortLinkTtlSeconds, long notFoundTtlSeconds) {}

    public record ClickTracker(int queueCapacity, int batchSize, long flushIntervalMs) {}

    public record RateLimit(boolean enabled, int redirectPerIpPerMinute) {}

    public record Geo(boolean enabled, String dbPath) {}
}

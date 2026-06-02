package com.memcyco.shortener.testsupport;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralised sample data for unit + integration tests. The factory methods return
 * plain Maps (no project types) so they remain compile-stable even as Agent A's
 * DTO/entity layer evolves. Tests that need real entities should construct them
 * inline via the entity class once it lands.
 */
public final class ShortLinkFixtures {

    public static final String DEFAULT_URL = "https://example.com/very/long/url/path?ref=test";
    public static final String OTHER_URL  = "https://memcyco.dev/products/safety";

    private ShortLinkFixtures() {}

    /** Body for POST /api/short-links using the default RANDOM_BASE62 strategy. */
    public static Map<String, Object> randomBase62Request() {
        Map<String, Object> body = new HashMap<>();
        body.put("originalUrl", DEFAULT_URL);
        body.put("strategy", "RANDOM_BASE62");
        body.put("tags", List.of("integration"));
        body.put("parameters", Map.of("length", 7));
        return body;
    }

    /** Body for POST /api/short-links using a custom alias. */
    public static Map<String, Object> customAliasRequest(String alias) {
        Map<String, Object> body = new HashMap<>();
        body.put("originalUrl", DEFAULT_URL);
        body.put("strategy", "CUSTOM_ALIAS");
        body.put("alias", alias);
        body.put("tags", List.of());
        body.put("parameters", Map.of("alias", alias));
        return body;
    }

    public static Map<String, Object> sequentialRequest() {
        Map<String, Object> body = new HashMap<>();
        body.put("originalUrl", DEFAULT_URL);
        body.put("strategy", "SEQUENTIAL");
        body.put("tags", List.of());
        body.put("parameters", Map.of());
        return body;
    }

    public static Map<String, Object> hashTruncRequest() {
        Map<String, Object> body = new HashMap<>();
        body.put("originalUrl", DEFAULT_URL);
        body.put("strategy", "HASH_TRUNC");
        body.put("tags", List.of());
        body.put("parameters", Map.of("length", 8));
        return body;
    }

    public static Instant futureExpiry() {
        return Instant.now().plus(7, ChronoUnit.DAYS);
    }

    public static Instant pastExpiry() {
        return Instant.now().minus(1, ChronoUnit.HOURS);
    }

    /** Sample click "data" JSONB blob — matches what the redirect path captures. */
    public static Map<String, Object> sampleClickData() {
        Map<String, Object> data = new HashMap<>();
        data.put("referer", "https://twitter.com");
        data.put("userAgent", "Mozilla/5.0 (TestAgent)");
        data.put("ip", "203.0.113.7");
        return data;
    }
}

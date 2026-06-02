package com.memcyco.shortener.shortlink.cache;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Minimum fields the redirect path needs. Kept small to maximise cache density.
 * {@code missingSentinel=true} represents a negative cache entry (404 absorbed
 * briefly to dampen short-code-scan traffic).
 */
public record CachedShortLink(
        Long id,
        String originalUrl,
        Instant expiresAt,
        Long maxClicks,
        Long clickCount,
        boolean active,
        boolean missingSentinel
) {
    public static CachedShortLink miss() {
        return new CachedShortLink(null, null, null, null, null, false, true);
    }

    public static CachedShortLink hit(Long id, String originalUrl, Instant expiresAt,
                                       Long maxClicks, Long clickCount, boolean active) {
        return new CachedShortLink(id, originalUrl, expiresAt, maxClicks, clickCount, active, false);
    }

    @JsonCreator
    public static CachedShortLink of(
            @JsonProperty("id") Long id,
            @JsonProperty("originalUrl") String originalUrl,
            @JsonProperty("expiresAt") Instant expiresAt,
            @JsonProperty("maxClicks") Long maxClicks,
            @JsonProperty("clickCount") Long clickCount,
            @JsonProperty("active") boolean active,
            @JsonProperty("missingSentinel") boolean missingSentinel) {
        return new CachedShortLink(id, originalUrl, expiresAt, maxClicks, clickCount, active, missingSentinel);
    }
}

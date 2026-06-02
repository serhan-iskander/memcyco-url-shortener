package com.memcyco.shortener.shortlink.dto;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory event handed from the redirect controller to the click tracker.
 * Captured synchronously from the request, persisted asynchronously.
 */
public record ClickEventDto(
        Long shortLinkId,
        Instant clickedAt,
        Map<String, Object> data
) {
    public static ClickEventDto of(Long shortLinkId, Instant clickedAt,
                                    String referer, String userAgent, String ip) {
        Map<String, Object> d = new HashMap<>();
        if (referer != null) {
            d.put("referer", referer);
        }
        if (userAgent != null) {
            d.put("userAgent", userAgent);
        }
        if (ip != null) {
            d.put("ip", ip);
        }
        return new ClickEventDto(shortLinkId, clickedAt, d);
    }
}

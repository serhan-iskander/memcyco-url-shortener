package com.memcyco.shortener.shortlink.dto;

import com.memcyco.shortener.shortlink.domain.LinkStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ShortLinkResponse(
        Long id,
        String shortCode,
        String shortUrl,
        String originalUrl,
        String strategy,
        Instant expiresAt,
        Long maxClicks,
        Long clickCount,
        List<String> tags,
        Map<String, Object> parameters,
        LinkStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}

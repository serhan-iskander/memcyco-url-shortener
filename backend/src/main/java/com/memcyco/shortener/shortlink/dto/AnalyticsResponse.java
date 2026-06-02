package com.memcyco.shortener.shortlink.dto;

import java.time.Instant;
import java.util.List;

public record AnalyticsResponse(
        Long shortLinkId,
        long totalClicks,
        long last24hClicks,
        long uniqueReferers,
        List<BucketPoint> series,
        List<ValueCount> topReferers,
        List<ValueCount> topUserAgents
) {
    public record BucketPoint(Instant bucket, long count) {}

    public record ValueCount(String value, long count) {}
}

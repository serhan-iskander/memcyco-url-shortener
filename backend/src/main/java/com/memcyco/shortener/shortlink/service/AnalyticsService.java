package com.memcyco.shortener.shortlink.service;

import com.memcyco.shortener.common.error.ShortLinkNotFoundException;
import com.memcyco.shortener.shortlink.dto.AnalyticsResponse;
import com.memcyco.shortener.shortlink.repo.AnalyticsJdbcRepository;
import com.memcyco.shortener.shortlink.repo.ShortLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final int TOP_N = 10;

    private final AnalyticsJdbcRepository analyticsRepo;
    private final ShortLinkRepository shortLinkRepo;
    private final Clock clock;

    public AnalyticsResponse forShortLink(Long id, String bucket, Instant from, Instant to) {
        if (!shortLinkRepo.existsById(id)) {
            throw new ShortLinkNotFoundException("short link " + id);
        }
        Instant now = Instant.now(clock);
        Instant effectiveFrom = from != null ? from : now.minus(Duration.ofDays(7));
        // 'to' is half-open: range is [from, to). Default to slightly past 'now'
        // so today's most recent bucket is included.
        Instant effectiveTo = to != null ? to : now.plus(Duration.ofMinutes(1));
        String effectiveBucket = bucket == null ? "hour" : bucket;

        List<AnalyticsResponse.BucketPoint> series =
                analyticsRepo.timeSeries(id, effectiveBucket, effectiveFrom, effectiveTo);
        List<AnalyticsResponse.ValueCount> topRef =
                analyticsRepo.topByJsonField(id, "referer", TOP_N);
        List<AnalyticsResponse.ValueCount> topUa =
                analyticsRepo.topByJsonField(id, "userAgent", TOP_N);

        return new AnalyticsResponse(
                id,
                analyticsRepo.totalClicks(id),
                analyticsRepo.clicksSince(id, now.minus(Duration.ofHours(24))),
                analyticsRepo.uniqueReferers(id),
                series,
                topRef,
                topUa
        );
    }
}

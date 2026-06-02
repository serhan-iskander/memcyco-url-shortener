package com.memcyco.shortener.shortlink.service;

import com.memcyco.shortener.config.AppProperties;
import com.memcyco.shortener.shortlink.cache.CachedShortLink;
import com.memcyco.shortener.shortlink.domain.LinkStatus;
import com.memcyco.shortener.shortlink.domain.ShortLink;
import com.memcyco.shortener.shortlink.dto.ShortLinkResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ShortLinkMapper {

    private final AppProperties props;
    private final Clock clock;

    public ShortLinkResponse toResponse(ShortLink sl) {
        return new ShortLinkResponse(
                sl.getId(),
                sl.getShortCode(),
                buildShortUrl(sl.getShortCode()),
                sl.getOriginalUrl(),
                sl.getStrategy(),
                sl.getExpiresAt(),
                sl.getMaxClicks(),
                sl.getClickCount(),
                Arrays.asList(sl.getTags() == null ? new String[0] : sl.getTags()),
                sl.getParameters() == null ? new HashMap<>() : new HashMap<>(sl.getParameters()),
                deriveStatus(sl),
                sl.getCreatedAt(),
                sl.getUpdatedAt()
        );
    }

    public CachedShortLink toCacheable(ShortLink sl) {
        return CachedShortLink.hit(sl.getId(), sl.getOriginalUrl(), sl.getExpiresAt(),
                sl.getMaxClicks(), sl.getClickCount(), sl.isActive());
    }

    public LinkStatus deriveStatus(ShortLink sl) {
        if (!sl.isActive() || sl.getDeletedAt() != null) {
            return LinkStatus.INACTIVE;
        }
        Instant now = Instant.now(clock);
        if (sl.getExpiresAt() != null && !now.isBefore(sl.getExpiresAt())) {
            return LinkStatus.EXPIRED;
        }
        if (sl.getMaxClicks() != null && sl.getClickCount() != null
                && sl.getClickCount() >= sl.getMaxClicks()) {
            return LinkStatus.EXHAUSTED;
        }
        return LinkStatus.ACTIVE;
    }

    public LinkStatus deriveStatus(CachedShortLink cached, long currentClickCount) {
        if (!cached.active()) {
            return LinkStatus.INACTIVE;
        }
        Instant now = Instant.now(clock);
        if (cached.expiresAt() != null && !now.isBefore(cached.expiresAt())) {
            return LinkStatus.EXPIRED;
        }
        if (cached.maxClicks() != null && currentClickCount >= cached.maxClicks()) {
            return LinkStatus.EXHAUSTED;
        }
        return LinkStatus.ACTIVE;
    }

    public String buildShortUrl(String code) {
        String base = props.baseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + code;
    }

    public Map<String, Object> safeParams(Map<String, Object> in) {
        return in == null ? new HashMap<>() : new HashMap<>(in);
    }
}

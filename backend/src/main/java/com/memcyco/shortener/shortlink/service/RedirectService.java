package com.memcyco.shortener.shortlink.service;

import com.memcyco.shortener.common.error.ShortLinkGoneException;
import com.memcyco.shortener.common.error.ShortLinkNotFoundException;
import com.memcyco.shortener.shortlink.cache.CachedShortLink;
import com.memcyco.shortener.shortlink.cache.ShortLinkCache;
import com.memcyco.shortener.shortlink.domain.LinkStatus;
import com.memcyco.shortener.shortlink.domain.ShortLink;
import com.memcyco.shortener.shortlink.repo.ShortLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resolves a short code to a destination URL with redirect semantics applied.
 * Hot path: Redis hit → status check → return. On miss, fall through to DB and warm.
 */
@Service
public class RedirectService {

    private final ShortLinkCache cache;
    private final ShortLinkRepository repo;
    private final ShortLinkMapper mapper;

    public RedirectService(ShortLinkCache cache, ShortLinkRepository repo, ShortLinkMapper mapper) {
        this.cache = cache;
        this.repo = repo;
        this.mapper = mapper;
    }

    /**
     * @return the destination URL and the short_link id (needed by the click tracker).
     * Throws {@link ShortLinkNotFoundException} (404) or {@link ShortLinkGoneException} (410).
     */
    public RedirectResult resolve(String shortCode) {
        Optional<CachedShortLink> cached = cache.get(shortCode);
        if (cached.isPresent()) {
            CachedShortLink hit = cached.get();
            if (hit.missingSentinel()) {
                throw new ShortLinkNotFoundException("Short code '" + shortCode + "' not found");
            }
            return decide(shortCode, hit);
        }

        ShortLink sl = loadFromDb(shortCode);
        if (sl == null) {
            cache.putMiss(shortCode);
            throw new ShortLinkNotFoundException("Short code '" + shortCode + "' not found");
        }
        CachedShortLink fresh = mapper.toCacheable(sl);
        cache.put(shortCode, fresh);
        return decide(shortCode, fresh);
    }

    @Transactional(readOnly = true)
    protected ShortLink loadFromDb(String shortCode) {
        return repo.findByShortCode(shortCode).orElse(null);
    }

    private RedirectResult decide(String shortCode, CachedShortLink cached) {
        long currentCount = currentClickCount(shortCode, cached);
        LinkStatus status = mapper.deriveStatus(cached, currentCount);
        if (status != LinkStatus.ACTIVE) {
            throw new ShortLinkGoneException(
                    "Short link is " + status.name().toLowerCase(), status);
        }
        return new RedirectResult(cached.id(), cached.originalUrl());
    }

    private long currentClickCount(String shortCode, CachedShortLink cached) {
        Long c = cache.currentClickCount(shortCode);
        if (c != null) {
            return c;
        }
        return cached.clickCount() == null ? 0L : cached.clickCount();
    }

    /** Called by the redirect controller AFTER the 302 is queued to bump the cached counter. */
    public void markRedirected(String shortCode) {
        cache.incrementClickCount(shortCode, 1);
    }

    public record RedirectResult(Long shortLinkId, String originalUrl) {}
}

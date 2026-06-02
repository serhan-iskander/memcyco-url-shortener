package com.memcyco.shortener.shortlink.service;

import com.memcyco.shortener.common.error.ShortLinkGoneException;
import com.memcyco.shortener.common.error.ShortLinkNotFoundException;
import com.memcyco.shortener.shortlink.cache.CachedShortLink;
import com.memcyco.shortener.shortlink.cache.ShortLinkCache;
import com.memcyco.shortener.shortlink.domain.LinkStatus;
import com.memcyco.shortener.shortlink.domain.ShortLink;
import com.memcyco.shortener.shortlink.repo.ShortLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resolves a short code to a destination URL with redirect semantics applied.
 * Hot path: Redis hit → status check → return. On miss, fall through to DB and warm.
 */
@Service
@RequiredArgsConstructor
public class RedirectService {

    private final ShortLinkCache cache;
    private final ShortLinkRepository repo;
    private final ShortLinkMapper mapper;

    /**
     * Resolve a short code, atomically count the redirect, and gate on max_clicks.
     *
     * <p>Order of operations is load-bearing:
     * <ol>
     *   <li>Load the cached entity (or DB → cache.put which resets the counter).</li>
     *   <li>Pre-check non-counter statuses (inactive / expired) — bail out before
     *       wasting an INCR on a link that can never serve.</li>
     *   <li><strong>Atomic Redis INCR</strong> — returns the new counter value.</li>
     *   <li>Derive total = base + newCounter and gate on max_clicks using THIS
     *       request's incremented value. Eliminates the TOCTOU race where two
     *       concurrent requests both read count-before-gate and both pass.</li>
     * </ol>
     *
     * @return the destination URL and the short_link id (needed by the click tracker).
     * Throws {@link ShortLinkNotFoundException} (404) or {@link ShortLinkGoneException} (410).
     */
    public RedirectResult resolve(String shortCode) {
        CachedShortLink hit = loadHitOrThrow(shortCode);

        // Bail out on non-counter statuses BEFORE incrementing — no point bumping
        // a counter for a link that's already inactive or past its expiration.
        rejectIfInactiveOrExpired(hit);

        // Atomic INCR. Returns the post-increment value — this is THE source of truth
        // for "how many redirects has this short link served including me".
        Long newCounter = cache.incrementClickCount(shortCode, 1);

        long base = hit.clickCount() == null ? 0L : hit.clickCount();
        long totalAfterMe = base + (newCounter == null ? 1L : newCounter);

        // Gate this click. ShortLinkMapper#deriveStatus uses count >= maxClicks for
        // EXHAUSTED (a link's *status* becomes EXHAUSTED when it has served its quota),
        // so we feed it the count BEFORE this click — i.e. "was the link already at
        // its limit when I arrived?". If yes, reject this click; the link reaches its
        // exact maxClicks budget instead of overshooting.
        long countBeforeThisClick = totalAfterMe - 1;
        LinkStatus status = mapper.deriveStatus(hit, countBeforeThisClick);
        if (status != LinkStatus.ACTIVE) {
            throw new ShortLinkGoneException(
                    "Short link is " + status.name().toLowerCase(), status);
        }
        return new RedirectResult(hit.id(), hit.originalUrl());
    }

    private CachedShortLink loadHitOrThrow(String shortCode) {
        Optional<CachedShortLink> cached = cache.get(shortCode);
        if (cached.isPresent()) {
            CachedShortLink hit = cached.get();
            if (hit.missingSentinel()) {
                throw new ShortLinkNotFoundException("Short code '" + shortCode + "' not found");
            }
            return hit;
        }
        ShortLink sl = loadFromDb(shortCode);
        if (sl == null) {
            cache.putMiss(shortCode);
            throw new ShortLinkNotFoundException("Short code '" + shortCode + "' not found");
        }
        CachedShortLink fresh = mapper.toCacheable(sl);
        cache.put(shortCode, fresh);   // also DELs the per-window counter
        return fresh;
    }

    private void rejectIfInactiveOrExpired(CachedShortLink hit) {
        LinkStatus s = mapper.deriveStatus(hit, hit.clickCount() == null ? 0L : hit.clickCount());
        if (s == LinkStatus.INACTIVE || s == LinkStatus.EXPIRED) {
            throw new ShortLinkGoneException("Short link is " + s.name().toLowerCase(), s);
        }
    }

    @Transactional(readOnly = true)
    protected ShortLink loadFromDb(String shortCode) {
        return repo.findByShortCode(shortCode).orElse(null);
    }

    public record RedirectResult(Long shortLinkId, String originalUrl) {}
}

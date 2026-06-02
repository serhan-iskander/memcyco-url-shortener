package com.memcyco.shortener.shortlink.service;

import com.memcyco.shortener.config.AppProperties;
import com.memcyco.shortener.shortlink.domain.LinkStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memcyco.shortener.shortlink.service.RedirectService;
import com.memcyco.shortener.shortlink.cache.ShortLinkCache;
import com.memcyco.shortener.shortlink.cache.CachedShortLink;
import com.memcyco.shortener.shortlink.repo.ShortLinkRepository;
import com.memcyco.shortener.shortlink.domain.ShortLink;
import com.memcyco.shortener.common.error.ShortLinkNotFoundException;
import com.memcyco.shortener.common.error.ShortLinkGoneException;

/**
 * Unit-level test for the cache → DB redirect resolution. The real RedirectService
 * does NOT call the click tracker (that's the controller's job — see
 * {@link com.memcyco.shortener.shortlink.api.RedirectController}); these tests pin
 * the cache hit / miss / negative-sentinel / status-gate behaviour.
 */
@ExtendWith(MockitoExtension.class)
class RedirectServiceTest {

    @Mock ShortLinkCache cache;
    @Mock ShortLinkRepository repository;

    private ShortLinkMapper mapper;
    private RedirectService service;

    private static final String CODE = "abc123";
    private static final String URL  = "https://example.com/landing";

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                "http://localhost:8080",
                new AppProperties.Cache(60, 30),
                new AppProperties.ClickTracker(1000, 100, 1000),
                new AppProperties.RateLimit(false, 60),
                new AppProperties.Geo(false, null)
        );
        // Use a real mapper so deriveStatus is exercised end-to-end.
        mapper = new ShortLinkMapper(props, Clock.systemUTC());
        service = new RedirectService(cache, repository, mapper);
    }

    // --- Cache hit ---------------------------------------------------------

    @Test
    @DisplayName("cache hit + active → returns URL without touching DB")
    void cacheHitActive() {
        CachedShortLink cached = CachedShortLink.hit(1L, URL, null, null, 0L, true);
        when(cache.get(CODE)).thenReturn(Optional.of(cached));

        RedirectService.RedirectResult result = service.resolve(CODE);

        assertThat(result.originalUrl()).isEqualTo(URL);
        assertThat(result.shortLinkId()).isEqualTo(1L);
        verify(repository, never()).findByShortCode(anyString());
    }

    // --- Cache miss → DB ---------------------------------------------------

    @Test
    @DisplayName("cache miss → DB hit → cache populated → URL returned")
    void cacheMissDbHit() {
        when(cache.get(CODE)).thenReturn(Optional.empty());
        ShortLink entity = mockActiveLink(1L, CODE, URL, null, null, 0L);
        when(repository.findByShortCode(CODE)).thenReturn(Optional.of(entity));

        RedirectService.RedirectResult result = service.resolve(CODE);

        assertThat(result.originalUrl()).isEqualTo(URL);
        verify(cache).put(eq(CODE), any(CachedShortLink.class));
    }

    // --- Bug 1 regression: per-window counter reset --------------------------

    /**
     * Pre-fix: shortlink:CODE expired naturally → cache MISS → repo returns row
     * with click_count=5 (DB column, already incremented by ClickBatchWriter) →
     * cache.put(...) WITHOUT resetting shortlink:count:CODE (still 5 from the
     * previous window) → next decide() saw base(5) + extra(5) = 10. Linked at
     * max=10 would 410 prematurely.
     *
     * Post-fix: cache.put MUST DEL the counter so the new window starts at 0.
     * Verify cache.put is invoked, then assert the test setup gives base+0
     * total when the counter is fresh (mocked to return 1L = the INCR-then-gate).
     */
    @Test
    @DisplayName("bug 1: cache miss → cache.put is called (which must also reset the counter)")
    void cacheMissResetsCounterOnPut() {
        when(cache.get(CODE)).thenReturn(Optional.empty());
        // Simulate the "TTL expired but DB has accumulated clicks" scenario.
        ShortLink entity = mockActiveLink(1L, CODE, URL, null, 10L, 5L);
        when(repository.findByShortCode(CODE)).thenReturn(Optional.of(entity));
        // After cache.put resets the counter, INCR returns 1 (this redirect is #6 overall).
        when(cache.incrementClickCount(CODE, 1L)).thenReturn(1L);

        RedirectService.RedirectResult result = service.resolve(CODE);

        // Did not 410 — base(5) + new(1) = 6 < max(10), correctly under the cap.
        assertThat(result.originalUrl()).isEqualTo(URL);
        // cache.put MUST be called on miss — its implementation is responsible for
        // also DEL-ing shortlink:count:* (see ShortLinkCache.put + its unit test).
        verify(cache).put(eq(CODE), any(CachedShortLink.class));
    }

    // --- Bug 2 regression: atomic INCR-then-gate (no TOCTOU) -----------------

    /**
     * Pre-fix: resolve() read the counter via cache.currentClickCount, gated,
     * then the controller called markRedirected() to INCR. Two parallel requests
     * at count=9 / max=10 both passed the gate before either INCRed → overshoot.
     *
     * Post-fix: resolve() INCRs first (atomic), gates on the post-increment
     * value. The 10th request sees newCounter=10 → total=10 ≤ max → OK.
     * The 11th request sees newCounter=11 → total=11 > max → 410.
     */
    @Test
    @DisplayName("bug 2: post-INCR value > max_clicks → 410 (atomic gate)")
    void incrementBeforeGateRejectsOvershoot() {
        // base=0 from cache; max=10; INCR returns 11 (the over-budget caller).
        CachedShortLink cached = CachedShortLink.hit(1L, URL, null, 10L, 0L, true);
        when(cache.get(CODE)).thenReturn(Optional.of(cached));
        when(cache.incrementClickCount(CODE, 1L)).thenReturn(11L);

        assertThatThrownBy(() -> service.resolve(CODE))
                .isInstanceOf(ShortLinkGoneException.class)
                .extracting("status").isEqualTo(LinkStatus.EXHAUSTED);
    }

    @Test
    @DisplayName("bug 2: post-INCR value == max_clicks → still ACTIVE (boundary)")
    void incrementBeforeGateAllowsLastClick() {
        CachedShortLink cached = CachedShortLink.hit(1L, URL, null, 10L, 0L, true);
        when(cache.get(CODE)).thenReturn(Optional.of(cached));
        when(cache.incrementClickCount(CODE, 1L)).thenReturn(10L);

        RedirectService.RedirectResult result = service.resolve(CODE);
        assertThat(result.originalUrl()).isEqualTo(URL);
    }

    @Test
    @DisplayName("expired link: bail out BEFORE incrementing (don't waste INCRs)")
    void expiredLinkDoesNotIncrement() {
        CachedShortLink cached = CachedShortLink.hit(
                1L, URL, Instant.now().minus(1, ChronoUnit.HOURS), null, 0L, true);
        when(cache.get(CODE)).thenReturn(Optional.of(cached));

        assertThatThrownBy(() -> service.resolve(CODE))
                .isInstanceOf(ShortLinkGoneException.class);
        verify(cache, never()).incrementClickCount(anyString(), any(Long.class));
    }

    // --- Expired -----------------------------------------------------------

    @Test
    @DisplayName("expired link → ShortLinkGoneException")
    void expiredLinkRaisesGone() {
        CachedShortLink cached = CachedShortLink.hit(
                1L, URL,
                Instant.now().minus(1, ChronoUnit.HOURS), // expired
                null, 0L, true);
        when(cache.get(CODE)).thenReturn(Optional.of(cached));

        assertThatThrownBy(() -> service.resolve(CODE))
                .isInstanceOf(ShortLinkGoneException.class);
    }

    // --- Exhausted ---------------------------------------------------------

    @Test
    @DisplayName("click-exhausted link → ShortLinkGoneException")
    void exhaustedLinkRaisesGone() {
        // base=5 from cache (already at max), max=5; INCR returns 6 → 410.
        CachedShortLink cached = CachedShortLink.hit(1L, URL, null, 5L, 5L, true);
        when(cache.get(CODE)).thenReturn(Optional.of(cached));
        when(cache.incrementClickCount(CODE, 1L)).thenReturn(6L);

        assertThatThrownBy(() -> service.resolve(CODE))
                .isInstanceOf(ShortLinkGoneException.class)
                .extracting("status").isEqualTo(LinkStatus.EXHAUSTED);
    }

    // --- Inactive ----------------------------------------------------------

    @Test
    @DisplayName("inactive link → ShortLinkGoneException")
    void inactiveLinkRaisesGone() {
        CachedShortLink cached = CachedShortLink.hit(1L, URL, null, null, 0L, false);
        when(cache.get(CODE)).thenReturn(Optional.of(cached));

        assertThatThrownBy(() -> service.resolve(CODE))
                .isInstanceOf(ShortLinkGoneException.class)
                .extracting("status").isEqualTo(LinkStatus.INACTIVE);
    }

    // --- Not found ---------------------------------------------------------

    @Test
    @DisplayName("not found in cache or DB → exception + negative cache populated")
    void notFoundPopulatesNegativeCache() {
        when(cache.get(CODE)).thenReturn(Optional.empty());
        when(repository.findByShortCode(CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(CODE))
                .isInstanceOf(ShortLinkNotFoundException.class);

        verify(cache).putMiss(CODE);
    }

    @Test
    @DisplayName("cached negative-sentinel short-circuits without touching DB")
    void cachedNotFoundShortCircuits() {
        when(cache.get(CODE)).thenReturn(Optional.of(CachedShortLink.miss()));

        assertThatThrownBy(() -> service.resolve(CODE))
                .isInstanceOf(ShortLinkNotFoundException.class);

        verify(repository, never()).findByShortCode(anyString());
    }

    // --- Soft delete -------------------------------------------------------

    @Test
    @DisplayName("soft-deleted link is treated as not found")
    void softDeletedTreatedAsNotFound() {
        when(cache.get(CODE)).thenReturn(Optional.empty());
        // Repository's @SQLRestriction filters by deleted_at IS NULL → empty.
        when(repository.findByShortCode(CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(CODE))
                .isInstanceOf(ShortLinkNotFoundException.class);
    }

    // --- Helpers -----------------------------------------------------------

    private ShortLink mockActiveLink(
            long id, String code, String url, Instant expiresAt, Long maxClicks, long clickCount
    ) {
        ShortLink link = org.mockito.Mockito.mock(ShortLink.class);
        when(link.getId()).thenReturn(id);
        when(link.getOriginalUrl()).thenReturn(url);
        when(link.getExpiresAt()).thenReturn(expiresAt);
        when(link.getMaxClicks()).thenReturn(maxClicks);
        when(link.getClickCount()).thenReturn(clickCount);
        when(link.isActive()).thenReturn(true);
        return link;
    }
}

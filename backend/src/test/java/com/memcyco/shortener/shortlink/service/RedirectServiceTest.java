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
        CachedShortLink cached = CachedShortLink.hit(1L, URL, null, 5L, 5L, true);
        when(cache.get(CODE)).thenReturn(Optional.of(cached));
        // Make the live counter mirror the cached count so the gate triggers.
        when(cache.currentClickCount(CODE)).thenReturn(5L);

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

    // --- markRedirected ----------------------------------------------------

    @Test
    @DisplayName("markRedirected bumps the cache click counter")
    void markRedirectedIncrementsCounter() {
        service.markRedirected(CODE);
        verify(cache).incrementClickCount(CODE, 1L);
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

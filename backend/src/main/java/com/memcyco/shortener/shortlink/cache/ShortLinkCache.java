package com.memcyco.shortener.shortlink.cache;

import com.memcyco.shortener.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
public class ShortLinkCache {

    private static final Logger log = LoggerFactory.getLogger(ShortLinkCache.class);
    private static final String KEY_PREFIX = "shortlink:";
    private static final String COUNT_KEY_PREFIX = "shortlink:count:";

    private final RedisTemplate<String, CachedShortLink> redis;
    private final StringRedisTemplate stringRedis;
    private final AppProperties props;
    private final Clock clock;

    public ShortLinkCache(RedisTemplate<String, CachedShortLink> redis,
                          StringRedisTemplate stringRedis,
                          AppProperties props,
                          Clock clock) {
        this.redis = redis;
        this.stringRedis = stringRedis;
        this.props = props;
        this.clock = clock;
    }

    public Optional<CachedShortLink> get(String code) {
        try {
            CachedShortLink v = redis.opsForValue().get(key(code));
            return Optional.ofNullable(v);
        } catch (DataAccessException ex) {
            log.warn("Redis GET failed for code={} — falling back to DB: {}", code, ex.getMessage());
            return Optional.empty();
        }
    }

    public void put(String code, CachedShortLink value) {
        Duration ttl = computeTtl(value);
        try {
            redis.opsForValue().set(key(code), value, ttl);
        } catch (DataAccessException ex) {
            log.warn("Redis SET failed for code={}: {}", code, ex.getMessage());
        }
    }

    public void putMiss(String code) {
        try {
            redis.opsForValue().set(key(code), CachedShortLink.miss(),
                    Duration.ofSeconds(props.cache().notFoundTtlSeconds()));
        } catch (DataAccessException ex) {
            log.warn("Redis negative-cache SET failed for code={}: {}", code, ex.getMessage());
        }
    }

    public void invalidate(String code) {
        try {
            redis.delete(key(code));
            stringRedis.delete(countKey(code));
        } catch (DataAccessException ex) {
            log.warn("Redis DEL failed for code={}: {}", code, ex.getMessage());
        }
    }

    /**
     * Bumps the per-code click counter in Redis. Returns the new count so the
     * redirect path can gate on max_clicks without re-reading the row.
     * Uses StringRedisTemplate so INCRBY writes a raw numeric string that we can
     * read back without the typed serializer trying to deserialize it as JSON.
     */
    public Long incrementClickCount(String code, long delta) {
        try {
            return stringRedis.opsForValue().increment(countKey(code), delta);
        } catch (DataAccessException ex) {
            log.warn("Redis INCRBY failed for code={}: {}", code, ex.getMessage());
            return null;
        }
    }

    public Long currentClickCount(String code) {
        try {
            CachedShortLink v = redis.opsForValue().get(key(code));
            long base = (v == null || v.clickCount() == null) ? 0L : v.clickCount();
            String counterRaw = stringRedis.opsForValue().get(countKey(code));
            long extra = (counterRaw == null) ? 0L : Long.parseLong(counterRaw);
            return base + extra;
        } catch (DataAccessException | NumberFormatException ex) {
            return null;
        }
    }

    private Duration computeTtl(CachedShortLink value) {
        long base = props.cache().shortLinkTtlSeconds();
        if (value.expiresAt() == null) {
            return Duration.ofSeconds(base);
        }
        long secondsToExpiry = value.expiresAt().getEpochSecond() - clock.instant().getEpochSecond();
        if (secondsToExpiry <= 0) {
            return Duration.ofSeconds(1); // already expired; cache briefly to absorb dogpiles
        }
        return Duration.ofSeconds(Math.min(base, secondsToExpiry));
    }

    private String key(String code) { return KEY_PREFIX + code; }

    private String countKey(String code) { return COUNT_KEY_PREFIX + code; }

    /** Test/debug helper — exposed for the analytics layer to surface near-real-time counts. */
    public Instant now() { return Instant.now(clock); }
}

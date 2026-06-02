package com.memcyco.shortener.common.ratelimit;

import com.memcyco.shortener.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Per-IP token-bucket rate limiter on the redirect endpoint. Backed by Redis
 * (Bucket4j + Lettuce) so it works across replicas. Feature-flagged by
 * {@code app.rate-limit.enabled} (on by default); when off, this is a no-op
 * pass-through.
 *
 * <p><strong>Fail-open:</strong> the redirect path must stay available even when
 * Redis is down. If the limiter can't reach Redis it logs and lets traffic through,
 * backing off for a cooldown so we don't pay a connection timeout on every redirect.
 * Rate limiting is best-effort protection, never a hard dependency of the hot path.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    /** After a Redis failure, skip the limiter entirely for this long before retrying. */
    private static final long DEGRADE_COOLDOWN_SECONDS = 30;
    private static final long DEGRADE_COOLDOWN_NANOS =
            Duration.ofSeconds(DEGRADE_COOLDOWN_SECONDS).toNanos();

    private final AppProperties props;
    private final String redisHost;
    private final int redisPort;
    private volatile ProxyManager<String> proxyManager;
    private volatile RedisClient redisClient;
    private volatile StatefulRedisConnection<String, byte[]> connection;
    /** {@code System.nanoTime()} deadline until which the limiter stays in fail-open mode. */
    private volatile long degradedUntilNanos;

    public RateLimitInterceptor(AppProperties props,
                                @Value("${spring.data.redis.host:localhost}") String redisHost,
                                @Value("${spring.data.redis.port:6379}") int redisPort) {
        this.props = props;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        // Seed to a past instant so the overflow-safe deadline check reads "not degraded"
        // from the first request (nanoTime can be negative at JVM start).
        this.degradedUntilNanos = System.nanoTime();
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if (!props.rateLimit().enabled()) {
            return true;
        }
        // Within the post-failure cooldown: stay out of Redis's way and fail open.
        if (System.nanoTime() - degradedUntilNanos < 0) {
            return true;
        }

        ConsumptionProbe probe;
        try {
            ensureInitialised();
            String key = "ratelimit:redirect:" + clientIp(request);

            Supplier<BucketConfiguration> config = () -> BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(props.rateLimit().redirectPerIpPerMinute())
                            .refillIntervally(props.rateLimit().redirectPerIpPerMinute(), Duration.ofMinutes(1))
                            .build())
                    .build();

            Bucket bucket = proxyManager.builder().build(key, config);
            probe = bucket.tryConsumeAndReturnRemaining(1);
        } catch (RuntimeException ex) {
            // Redis unreachable or Bucket4j wiring failed: never take down redirects for it.
            degradedUntilNanos = System.nanoTime() + DEGRADE_COOLDOWN_NANOS;
            log.warn("Rate limiter unavailable — failing open for {}s: {}",
                    DEGRADE_COOLDOWN_SECONDS, ex.getMessage());
            return true;
        }

        if (probe.isConsumed()) {
            response.addHeader("X-RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
            return true;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.addHeader("Retry-After",
                Long.toString(Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds() + 1));
        response.setContentType("application/problem+json");
        response.getWriter().write(
                "{\"type\":\"https://memcyco.dev/errors/rate-limited\","
                        + "\"title\":\"Too many requests\",\"status\":429,"
                        + "\"detail\":\"Per-IP rate limit exceeded for redirect endpoint.\"}");
        return false;
    }

    private synchronized void ensureInitialised() {
        if (proxyManager != null) {
            return;
        }
        try {
            this.redisClient = RedisClient.create("redis://" + redisHost + ":" + redisPort);
            RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
            this.connection = redisClient.connect(codec);
            this.proxyManager = LettuceBasedProxyManager.builderFor(connection).build();
        } catch (RuntimeException ex) {
            log.warn("Rate-limit Redis init failed — disabling for this process: {}", ex.getMessage());
            throw ex;
        }
    }

    private String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return (comma > 0 ? fwd.substring(0, comma) : fwd).trim();
        }
        return req.getRemoteAddr() == null ? "unknown" : req.getRemoteAddr();
    }
}

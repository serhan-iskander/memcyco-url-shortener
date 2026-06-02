package com.memcyco.shortener.ratelimit;

import com.memcyco.shortener.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bonus — rate limiter triggers a 429 after the configured threshold.
 *
 * <p>Property override enables the feature for this test only. If Bucket4j wiring
 * isn't in place yet, this test will fail compilation or with 200/302 instead of
 * 429 — flag for Phase 3.
 */
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        // Lower the threshold so the test isn't slow.
        "app.rate-limit.redirect-per-ip-per-minute=5"
})
class RateLimitIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("exceeding the per-minute redirect threshold → 429")
    void exceedingThresholdReturns429() {
        Map<String, Object> created = createLink(
                "https://example.com/landing", "ratelimit-it", null, null);
        String code = (String) created.get("shortCode");

        int seenLimit = 0;
        for (int i = 0; i < 20; i++) {
            ResponseEntity<Void> r = restTemplate.getForEntity(
                    baseUrl() + "/" + code, Void.class);
            if (r.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                seenLimit++;
            }
        }

        assertThat(seenLimit)
                .as("at least one request must have been rate-limited")
                .isGreaterThan(0);
    }
}

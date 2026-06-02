package com.memcyco.shortener.shortlink.api;

import com.memcyco.shortener.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Analytics aggregation correctness — seeds the clicks table directly across
 * multiple hours and asserts the time-series + breakdown buckets line up.
 */
class AnalyticsControllerIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("series buckets by hour, top referers / user-agents aggregated")
    void seriesAndBreakdowns() {
        Map<String, Object> created = createLink(
                "https://example.com/landing", "analytics-it", null, null);
        long linkId = ((Number) created.get("id")).longValue();

        Instant now = Instant.now();
        // 3 clicks at H-2 from twitter, 2 clicks at H-1 from facebook.
        seedClicks(linkId, now.minus(2, ChronoUnit.HOURS), 3,
                "https://twitter.com", "Mozilla/5.0 (UA-twitter)");
        seedClicks(linkId, now.minus(1, ChronoUnit.HOURS), 2,
                "https://facebook.com", "Mozilla/5.0 (UA-facebook)");

        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl() + "/api/short-links/" + linkId + "/analytics?bucket=hour", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(((Number) body.get("totalClicks")).intValue()).isEqualTo(5);

        List<?> series = (List<?>) body.get("series");
        assertThat(series).hasSizeGreaterThanOrEqualTo(2);
        int totalFromSeries = series.stream()
                .mapToInt(b -> ((Number) ((Map<?, ?>) b).get("count")).intValue())
                .sum();
        assertThat(totalFromSeries).isEqualTo(5);

        List<?> topReferers = (List<?>) body.get("topReferers");
        assertThat(topReferers).extracting(b -> ((Map<?, ?>) b).get("value"))
                .anyMatch(v -> "https://twitter.com".equals(v))
                .anyMatch(v -> "https://facebook.com".equals(v));

        List<?> topUAs = (List<?>) body.get("topUserAgents");
        assertThat(topUAs).extracting(b -> ((Map<?, ?>) b).get("value"))
                .anyMatch(v -> "Mozilla/5.0 (UA-twitter)".equals(v))
                .anyMatch(v -> "Mozilla/5.0 (UA-facebook)".equals(v));
    }

    @Test
    @DisplayName("day bucket coalesces same-day clicks")
    void dayBucket() {
        Map<String, Object> created = createLink(
                "https://example.com/landing", "analytics-day", null, null);
        long linkId = ((Number) created.get("id")).longValue();

        Instant now = Instant.now();
        seedClicks(linkId, now.minus(20, ChronoUnit.HOURS), 4, "ref", "ua");
        seedClicks(linkId, now.minus(2, ChronoUnit.HOURS),  3, "ref", "ua");

        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl() + "/api/short-links/" + linkId + "/analytics?bucket=day", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        // 7 clicks total; depending on day boundary they may straddle 1 or 2 day-buckets.
        assertThat(((Number) body.get("totalClicks")).intValue()).isEqualTo(7);
        List<?> series = (List<?>) body.get("series");
        assertThat(series).isNotEmpty();
        assertThat(series).hasSizeLessThanOrEqualTo(2);
    }

    /** Direct DB seed — simulates click rows the async writer would have produced. */
    private void seedClicks(long linkId, Instant at, int count, String referer, String ua) {
        String json = String.format(
                "{\"referer\":\"%s\",\"userAgent\":\"%s\",\"ip\":\"203.0.113.7\"}",
                referer, ua);
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                    "INSERT INTO clicks (short_link_id, clicked_at, data) " +
                            "VALUES (?, ?, ?::jsonb)",
                    linkId, Timestamp.from(at), json);
        }
    }
}

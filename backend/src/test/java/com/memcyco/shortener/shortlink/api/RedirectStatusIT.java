package com.memcyco.shortener.shortlink.api;

import com.memcyco.shortener.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redirect endpoint status code semantics: 404 / 410 for the not-happy paths.
 * Asserts the RFC 7807 content type on error responses.
 */
class RedirectStatusIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("expired link → 410 Gone with application/problem+json body")
    void expiredLinkReturns410() {
        // Insert directly so the row's expires_at is unambiguously in the past
        // (the create API rejects past timestamps by design).
        jdbcTemplate.update(
                "INSERT INTO short_links (short_code, original_url, strategy, expires_at, active) " +
                        "VALUES (?, ?, ?, ?, true)",
                "expired1", "https://example.com", "CUSTOM_ALIAS",
                java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS))
        );

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/expired1", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("click-exhausted link → 410 after maxClicks redirects")
    void exhaustedLinkReturns410() {
        Map<String, Object> created = createLink(
                "https://example.com/landing", "exhaust1", null, 2L);
        long linkId = ((Number) created.get("id")).longValue();
        String code = (String) created.get("shortCode");

        // Two successful redirects (within maxClicks).
        for (int i = 0; i < 2; i++) {
            ResponseEntity<Void> r = restTemplate.getForEntity(
                    baseUrl() + "/" + code, Void.class);
            assertThat(r.getStatusCode().is3xxRedirection() || r.getStatusCode().is2xxSuccessful())
                    .as("redirect %d should not be Gone", i).isTrue();
        }

        // Wait for click_count to catch up so the gate triggers on the 3rd call.
        awaitClickCount(linkId, 2);
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Long cc = jdbcTemplate.queryForObject(
                            "SELECT click_count FROM short_links WHERE id = ?",
                            Long.class, linkId);
                    assertThat(cc).isEqualTo(2L);
                });
        // The service tracks the live count in Redis (incrementClickCount) so the
        // exhausted gate fires without needing manual cache invalidation here.

        ResponseEntity<String> third = restTemplate.getForEntity(
                baseUrl() + "/" + code, String.class);
        assertThat(third.getStatusCode()).isEqualTo(HttpStatus.GONE);
    }

    @Test
    @DisplayName("missing short code → 404")
    void missingCodeReturns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/does-not-exist-xyz", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("soft-deleted link behaves like 404")
    void softDeletedReturns404() {
        Map<String, Object> created = createLink(
                "https://example.com/landing", "softdel1", null, null);
        long linkId = ((Number) created.get("id")).longValue();

        ResponseEntity<Void> del = restTemplate.exchange(
                baseUrl() + "/api/short-links/" + linkId,
                org.springframework.http.HttpMethod.DELETE,
                org.springframework.http.HttpEntity.EMPTY,
                Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/softdel1", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

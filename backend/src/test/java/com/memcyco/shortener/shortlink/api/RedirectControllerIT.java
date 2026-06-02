package com.memcyco.shortener.shortlink.api;

import com.memcyco.shortener.AbstractIntegrationTest;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import jakarta.persistence.EntityManagerFactory;

import java.net.HttpURLConnection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full redirect-path flow against real Postgres + Redis. This is the load-bearing
 * integration test for the system — exercises async click tracking, cache writes,
 * and 302 response semantics.
 */
class RedirectControllerIT extends AbstractIntegrationTest {

    @Autowired EntityManagerFactory entityManagerFactory;

    /** Don't follow 3xx — the test must see the 302 itself. */
    private TestRestTemplate noFollow;

    @BeforeEach
    void noFollowClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod)
                    throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        TestRestTemplate template = new TestRestTemplate();
        template.getRestTemplate().setRequestFactory(factory);
        this.noFollow = template;
    }

    @Test
    @DisplayName("create link → GET /{code} → 302 + Location + click row appears")
    void redirectHappyPath() {
        Map<String, Object> created = createLink(
                "https://example.com/landing", "redir-it-happy", null, null);

        long linkId = ((Number) created.get("id")).longValue();
        String code = (String) created.get("shortCode");

        ResponseEntity<Void> response = noFollow.getForEntity(
                baseUrl() + "/" + code, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString())
                .isEqualTo("https://example.com/landing");

        awaitClickCount(linkId, 1);

        Map<String, Object> click = jdbcTemplate.queryForMap(
                "SELECT data FROM clicks WHERE short_link_id = ?", linkId);
        assertThat(click.get("data")).isNotNull();
    }

    @Test
    @DisplayName("five sequential redirects → click_count column eventually reaches 5")
    void fiveRedirectsBumpClickCount() {
        Map<String, Object> created = createLink(
                "https://example.com/landing", "redir-it-five", null, null);

        long linkId = ((Number) created.get("id")).longValue();
        String code = (String) created.get("shortCode");

        for (int i = 0; i < 5; i++) {
            ResponseEntity<Void> r = noFollow.getForEntity(baseUrl() + "/" + code, Void.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        }

        awaitClickCount(linkId, 5);

        // click_count on the row should also reach 5 (eventually consistent).
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Long cc = jdbcTemplate.queryForObject(
                            "SELECT click_count FROM short_links WHERE id = ?",
                            Long.class, linkId);
                    assertThat(cc).isEqualTo(5L);
                });
    }

    @Test
    @DisplayName("cache hit path: second redirect issues zero short_links SELECTs")
    void cacheHitSkipsDb() {
        Map<String, Object> created = createLink(
                "https://example.com/landing", "redir-it-cache", null, null);
        String code = (String) created.get("shortCode");

        // Warm the cache + reset stats. Hibernate stats are enabled via
        // application-test.yml (generate_statistics: true).
        SessionFactory sf = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics stats = sf.getStatistics();

        // First call — populates cache.
        noFollow.getForEntity(baseUrl() + "/" + code, Void.class);
        stats.clear();

        // Second call — should be a pure cache hit. Zero new prepared statements.
        ResponseEntity<Void> second = noFollow.getForEntity(baseUrl() + "/" + code, Void.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(stats.getPrepareStatementCount())
                .as("second redirect must not hit the DB for short_links")
                .isZero();
    }
}

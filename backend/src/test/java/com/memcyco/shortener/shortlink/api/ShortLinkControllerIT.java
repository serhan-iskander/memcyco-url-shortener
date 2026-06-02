package com.memcyco.shortener.shortlink.api;

import com.memcyco.shortener.AbstractIntegrationTest;
import com.memcyco.shortener.testsupport.ShortLinkFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRUD surface for /api/short-links: create, read, update, delete, list filters.
 */
class ShortLinkControllerIT extends AbstractIntegrationTest {

    private static final HttpHeaders JSON;
    static {
        JSON = new HttpHeaders();
        JSON.setContentType(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("POST create with RANDOM_BASE62 → 201 + correct response shape")
    void createRandomBase62() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/short-links",
                HttpMethod.POST,
                new HttpEntity<>(ShortLinkFixtures.randomBase62Request(), JSON),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("id")).isNotNull();
        assertThat(body.get("shortCode")).asString().matches("[A-Za-z0-9]+");
        assertThat(body.get("shortUrl")).asString().contains((String) body.get("shortCode"));
        assertThat(body.get("originalUrl")).isEqualTo(ShortLinkFixtures.DEFAULT_URL);
        assertThat(body.get("strategy")).isEqualTo("RANDOM_BASE62");
        assertThat(body.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("POST create with CUSTOM_ALIAS + duplicate → 409 ProblemDetail")
    void duplicateAliasReturns409() {
        // first one succeeds
        ResponseEntity<Map> first = restTemplate.exchange(
                baseUrl() + "/api/short-links",
                HttpMethod.POST,
                new HttpEntity<>(ShortLinkFixtures.customAliasRequest("dupe-it"), JSON),
                Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // second collides
        ResponseEntity<Map> second = restTemplate.exchange(
                baseUrl() + "/api/short-links",
                HttpMethod.POST,
                new HttpEntity<>(ShortLinkFixtures.customAliasRequest("dupe-it"), JSON),
                Map.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        Map<?, ?> body = second.getBody();
        assertThat(body).isNotNull();
        assertThat(String.valueOf(body.get("type"))).contains("duplicate-alias");
    }

    @Test
    @DisplayName("POST create with invalid URL → 400 with field errors")
    void invalidUrlReturns400() {
        Map<String, Object> req = new HashMap<>(ShortLinkFixtures.randomBase62Request());
        req.put("originalUrl", "not a url");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/short-links",
                HttpMethod.POST,
                new HttpEntity<>(req, JSON),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.keySet()).anyMatch("errors"::equals);
    }

    @Test
    @DisplayName("PUT update mutates allowed fields; shortCode is immutable")
    void updateMutableFieldsOnly() {
        Map<String, Object> created = createLink(
                "https://example.com/old", "update-it", null, null);
        long id = ((Number) created.get("id")).longValue();
        String originalCode = (String) created.get("shortCode");

        Map<String, Object> update = new HashMap<>();
        update.put("originalUrl", "https://example.com/new");
        update.put("maxClicks", 100);
        update.put("tags", List.of("v2"));
        // Attempt to change shortCode — server must ignore.
        update.put("shortCode", "should-be-ignored");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/short-links/" + id,
                HttpMethod.PUT,
                new HttpEntity<>(update, JSON),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("originalUrl")).isEqualTo("https://example.com/new");
        assertThat(body.get("shortCode")).isEqualTo(originalCode);
    }

    @Test
    @DisplayName("DELETE soft-deletes; subsequent GET → 404 and redirect → 404")
    void deleteSoftRemoves() {
        Map<String, Object> created = createLink(
                "https://example.com/landing", "delete-it", null, null);
        long id = ((Number) created.get("id")).longValue();

        ResponseEntity<Void> del = restTemplate.exchange(
                baseUrl() + "/api/short-links/" + id,
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> getResp = restTemplate.getForEntity(
                baseUrl() + "/api/short-links/" + id, Map.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Void> redir = restTemplate.getForEntity(
                baseUrl() + "/delete-it", Void.class);
        assertThat(redir.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET list with tag filter returns only matching items")
    void listWithTagFilter() {
        // Create two links with different tags.
        Map<String, Object> req1 = new HashMap<>(ShortLinkFixtures.randomBase62Request());
        req1.put("tags", List.of("campaign-q4"));
        restTemplate.exchange(baseUrl() + "/api/short-links", HttpMethod.POST,
                new HttpEntity<>(req1, JSON), Map.class);

        Map<String, Object> req2 = new HashMap<>(ShortLinkFixtures.randomBase62Request());
        req2.put("tags", List.of("internal"));
        restTemplate.exchange(baseUrl() + "/api/short-links", HttpMethod.POST,
                new HttpEntity<>(req2, JSON), Map.class);

        ResponseEntity<Map> resp = restTemplate.getForEntity(
                baseUrl() + "/api/short-links?tag=campaign-q4", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = resp.getBody();
        assertThat(body).isNotNull();
        List<?> items = (List<?>) body.get("items");
        assertThat(items).hasSize(1);
    }

    @Test
    @DisplayName("GET list with status=EXPIRED filter returns only expired items")
    void listWithStatusFilter() {
        // Insert an expired row directly.
        jdbcTemplate.update(
                "INSERT INTO short_links (short_code, original_url, strategy, expires_at) " +
                        "VALUES (?, ?, ?, ?)",
                "old-expired", "https://example.com/old", "CUSTOM_ALIAS",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(3600))
        );
        createLink("https://example.com/live", "still-live", null, null);

        ResponseEntity<Map> resp = restTemplate.getForEntity(
                baseUrl() + "/api/short-links?status=EXPIRED", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = resp.getBody();
        assertThat(body).isNotNull();
        List<?> items = (List<?>) body.get("items");
        assertThat(items).hasSize(1);
    }
}

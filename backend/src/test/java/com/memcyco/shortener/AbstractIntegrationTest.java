package com.memcyco.shortener;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Base for all integration tests. Boots the full Spring context against shared
 * Postgres + Redis Testcontainers (started once per JVM and reused across classes).
 *
 * <p>Subclasses get a configured {@link TestRestTemplate} and convenience helpers
 * for creating links and waiting on the async click pipeline.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    // Static / singleton containers reused across every test class in the JVM.
    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("memcyco")
                    .withUsername("memcyco")
                    .withPassword("memcyco")
                    .withReuse(true);

    @SuppressWarnings("resource")
    protected static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"))
                    .withReuse(true);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Value("${local.server.port}")
    protected int port;

    /**
     * Truncate domain tables before each test so suites don't bleed into each other.
     * Containers are reused, but the data is reset per test.
     */
    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("TRUNCATE TABLE clicks RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE short_links RESTART IDENTITY CASCADE");
    }

    // --- Helpers -----------------------------------------------------------

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * Create a short link via the public REST API. Returns the JSON body as a Map so
     * tests can assert without depending on Agent A's DTO classes.
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> createLink(
            String originalUrl,
            String alias,
            Instant expiresAt,
            Long maxClicks
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("originalUrl", originalUrl);
        if (alias != null) {
            body.put("strategy", "CUSTOM_ALIAS");
            body.put("alias", alias);
        } else {
            body.put("strategy", "RANDOM_BASE62");
        }
        if (expiresAt != null) {
            body.put("expiresAt", expiresAt.toString());
        }
        if (maxClicks != null) {
            body.put("maxClicks", maxClicks);
        }
        body.put("tags", List.of());
        body.put("parameters", Map.of());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/short-links",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("create short link returned %s with body %s",
                        response.getStatusCode(), response.getBody())
                .isTrue();
        return (Map<String, Object>) response.getBody();
    }

    /**
     * Block until the click_count for the given short link reaches {@code expected}
     * (or fail after a generous test timeout). Click writes are async — never sleep
     * a fixed duration, always poll the DB through this helper.
     */
    protected void awaitClickCount(long linkId, long expected) {
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> {
                    Long count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM clicks WHERE short_link_id = ?",
                            Long.class,
                            linkId
                    );
                    assertThat(count).isEqualTo(expected);
                });
    }
}

package com.memcyco.shortener.shortlink.api;

import com.memcyco.shortener.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StrategiesControllerIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("GET /api/strategies returns all 4 strategies with parameter schemas")
    void listsAllStrategies() {
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/api/strategies",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).extracting(s -> s.get("name"))
                .containsExactlyInAnyOrder(
                        "RANDOM_BASE62", "HASH_TRUNC", "SEQUENTIAL", "CUSTOM_ALIAS");
        // Each descriptor must include a parameterSchema (may be empty list).
        for (Map<String, Object> s : body) {
            assertThat(s).containsKey("parameterSchema");
            assertThat(s.get("parameterSchema")).isInstanceOf(List.class);
            assertThat(s).containsKey("displayName");
            assertThat(s).containsKey("description");
        }
    }
}

package com.memcyco.shortener.strategy;

import com.memcyco.shortener.AbstractIntegrationTest;
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
 * Bonus #1 — strategy parameter schema validation surfaces field errors at the API.
 */
class ParameterSchemaValidationIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("CUSTOM_ALIAS missing 'alias' param → 400 with field error on alias")
    void missingRequiredParamReturns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("originalUrl", "https://example.com");
        body.put("strategy", "CUSTOM_ALIAS");
        body.put("parameters", Map.of()); // missing required 'alias'
        body.put("tags", List.of());

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/short-links",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        Map<?, ?> respBody = response.getBody();
        assertThat(respBody).isNotNull();
        assertThat(respBody.keySet()).anyMatch("errors"::equals);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) respBody.get("errors");
        assertThat(errors).extracting(e -> e.get("field"))
                .anyMatch(f -> String.valueOf(f).contains("alias"));
    }

    @Test
    @DisplayName("RANDOM_BASE62 with length out of bounds → 400")
    void outOfBoundsParamReturns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("originalUrl", "https://example.com");
        body.put("strategy", "RANDOM_BASE62");
        body.put("parameters", Map.of("length", 999));
        body.put("tags", List.of());

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/short-links",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}

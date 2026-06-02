package com.memcyco.shortener.qr;

import com.memcyco.shortener.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bonus — QR endpoint returns a PNG.
 */
class QrEndpointIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("GET /api/short-links/{id}/qr → 200 image/png with PNG magic header")
    void qrReturnsPng() {
        Map<String, Object> created = createLink(
                "https://example.com/landing", "qr-it", null, null);
        long id = ((Number) created.get("id")).longValue();

        ResponseEntity<byte[]> response = restTemplate.getForEntity(
                baseUrl() + "/api/short-links/" + id + "/qr", byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);

        byte[] body = response.getBody();
        assertThat(body).isNotNull().hasSizeGreaterThan(100);
        // PNG magic: 89 50 4E 47 0D 0A 1A 0A
        assertThat(body[0]).isEqualTo((byte) 0x89);
        assertThat(body[1]).isEqualTo((byte) 0x50);
        assertThat(body[2]).isEqualTo((byte) 0x4E);
        assertThat(body[3]).isEqualTo((byte) 0x47);
    }
}

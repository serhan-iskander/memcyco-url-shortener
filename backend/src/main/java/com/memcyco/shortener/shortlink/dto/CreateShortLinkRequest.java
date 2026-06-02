package com.memcyco.shortener.shortlink.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CreateShortLinkRequest(
        @NotBlank
        @Size(max = 2048)
        @Pattern(regexp = "^https?://.+", message = "must be an http or https URL")
        String originalUrl,

        @NotBlank
        String strategy,

        // Validated against the strategy regex when strategy=CUSTOM_ALIAS in the service.
        String alias,

        Instant expiresAt,

        @Min(1)
        Long maxClicks,

        List<@Pattern(regexp = "^[a-zA-Z0-9_-]{1,32}$") String> tags,

        Map<String, Object> parameters
) {
}

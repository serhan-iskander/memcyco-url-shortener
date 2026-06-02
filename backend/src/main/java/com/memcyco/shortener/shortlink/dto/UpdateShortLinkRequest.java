package com.memcyco.shortener.shortlink.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * PATCH-style update. All fields nullable; only provided fields are applied.
 * short_code, strategy, alias and parameters are immutable post-creation.
 */
public record UpdateShortLinkRequest(
        @Size(max = 2048)
        @Pattern(regexp = "^https?://.+", message = "must be an http or https URL")
        String originalUrl,

        Instant expiresAt,

        @Min(1)
        Long maxClicks,

        List<@Pattern(regexp = "^[a-zA-Z0-9_-]{1,32}$") String> tags,

        Boolean active
) {
}

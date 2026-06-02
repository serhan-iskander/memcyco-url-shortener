package com.memcyco.shortener.shortlink.api;

import com.memcyco.shortener.shortlink.dto.ClickEventDto;
import com.memcyco.shortener.shortlink.service.RedirectService;
import com.memcyco.shortener.tracking.ClickTracker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;

@RestController
@RequiredArgsConstructor
@Tag(name = "Redirect", description = "Public redirect endpoint")
public class RedirectController {

    private final RedirectService redirectService;
    private final ClickTracker clickTracker;
    private final Clock clock;

    @GetMapping("/{shortCode:[a-zA-Z0-9_-]{1,32}}")
    @Operation(summary = "Resolve a short code and 302 to the original URL")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode,
                                         HttpServletRequest request) {
        RedirectService.RedirectResult result = redirectService.resolve(shortCode);

        // Fire-and-forget click capture. Synchronous metadata extraction, async DB write.
        ClickEventDto event = ClickEventDto.of(
                result.shortLinkId(),
                Instant.now(clock),
                request.getHeader("Referer"),
                request.getHeader("User-Agent"),
                clientIp(request));
        clickTracker.track(event);
        redirectService.markRedirected(shortCode);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(result.originalUrl()))
                .build();
    }

    private String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return (comma > 0 ? fwd.substring(0, comma) : fwd).trim();
        }
        return req.getRemoteAddr();
    }
}

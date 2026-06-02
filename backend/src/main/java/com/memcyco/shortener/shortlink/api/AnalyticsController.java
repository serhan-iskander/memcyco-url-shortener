package com.memcyco.shortener.shortlink.api;

import com.memcyco.shortener.shortlink.dto.AnalyticsResponse;
import com.memcyco.shortener.shortlink.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/short-links")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Aggregated click analytics per short link")
public class AnalyticsController {

    private final AnalyticsService service;

    @GetMapping("/{id}/analytics")
    @Operation(summary = "Aggregated analytics: time-series, top referers, top user-agents")
    public AnalyticsResponse analytics(
            @PathVariable Long id,
            @RequestParam(defaultValue = "hour") String bucket,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return service.forShortLink(id, bucket, from, to);
    }
}

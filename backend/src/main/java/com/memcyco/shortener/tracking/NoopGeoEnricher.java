package com.memcyco.shortener.tracking;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Default enricher when {@code app.geo.enabled} is false (or absent).
 * No-op so the rest of the tracking pipeline stays simple and unconditional.
 */
@Component
@ConditionalOnProperty(prefix = "app.geo", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopGeoEnricher implements GeoEnricher {
    @Override
    public void enrich(String ip, Map<String, Object> data) {
        // intentionally empty
    }
}

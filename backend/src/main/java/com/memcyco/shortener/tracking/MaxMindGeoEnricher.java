package com.memcyco.shortener.tracking;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.memcyco.shortener.config.AppProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;

/**
 * MaxMind GeoLite2 enricher. Activated by {@code app.geo.enabled=true}.
 * Reader is thread-safe; opened once at startup, closed at shutdown.
 * TODO: hot-reload on DB file change (Phase 3 if needed).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.geo", name = "enabled", havingValue = "true")
public class MaxMindGeoEnricher implements GeoEnricher {

    private static final Logger log = LoggerFactory.getLogger(MaxMindGeoEnricher.class);

    private final AppProperties props;
    private DatabaseReader reader;

    @PostConstruct
    void init() {
        File db = new File(props.geo().dbPath());
        if (!db.exists()) {
            log.warn("Geo DB not found at {} — enrichment will silently fail open.", db);
            return;
        }
        try {
            reader = new DatabaseReader.Builder(db).build();
            log.info("MaxMind GeoIP reader opened from {}", db);
        } catch (IOException e) {
            log.warn("Failed to open MaxMind DB {} — geo disabled: {}", db, e.getMessage());
        }
    }

    @Override
    public void enrich(String ip, Map<String, Object> data) {
        if (reader == null || ip == null || ip.isBlank()) {
            return;
        }
        try {
            CityResponse resp = reader.city(InetAddress.getByName(ip));
            if (resp.getCountry() != null && resp.getCountry().getIsoCode() != null) {
                data.put("country", resp.getCountry().getIsoCode());
            }
            if (resp.getCity() != null && resp.getCity().getName() != null) {
                data.put("city", resp.getCity().getName());
            }
        } catch (IOException | GeoIp2Exception | RuntimeException ex) {
            // Geo lookup must never fail the redirect — log at debug and drop.
            log.debug("Geo lookup failed for ip={}: {}", ip, ex.getMessage());
        }
    }

    @PreDestroy
    void close() {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                log.warn("Closing GeoIP reader failed — ignoring: {}", e.getMessage());
            }
        }
    }
}

package com.memcyco.shortener.tracking;

import com.memcyco.shortener.config.AppProperties;
import com.memcyco.shortener.shortlink.dto.ClickEventDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.memcyco.shortener.tracking.ClickTracker;

class ClickTrackerTest {

    private static AppProperties propsWithCapacity(int capacity) {
        return new AppProperties(
                "http://localhost:8080",
                new AppProperties.Cache(60, 30),
                new AppProperties.ClickTracker(capacity, 100, 1000),
                new AppProperties.RateLimit(false, 60),
                new AppProperties.Geo(false, null)
        );
    }

    @Test
    @DisplayName("accepts events under capacity")
    void acceptsEventsUnderCapacity() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ClickTracker tracker = new ClickTracker(propsWithCapacity(10),
                new NoopGeoEnricher(), registry);

        for (int i = 0; i < 5; i++) {
            tracker.track(sampleEvent(i));
        }

        assertThat(tracker.queueDepth()).isEqualTo(5);
        assertThat(registry.counter("memcyco_clicks_accepted_total").count()).isEqualTo(5.0);
        assertThat(registry.counter("memcyco_clicks_dropped_total").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("drops events when the queue is full and never throws")
    void dropsOnOverflowWithoutThrowing() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ClickTracker tracker = new ClickTracker(propsWithCapacity(3),
                new NoopGeoEnricher(), registry);

        assertThatCode(() -> {
            for (int i = 0; i < 100; i++) {
                tracker.track(sampleEvent(i));
            }
        }).doesNotThrowAnyException();

        // The queue capacity is 3 — at most 3 events should be retained.
        assertThat(tracker.queueDepth()).isLessThanOrEqualTo(3);
        // We should have observed many drops.
        assertThat(registry.counter("memcyco_clicks_dropped_total").count())
                .isGreaterThanOrEqualTo(90.0);
    }

    @Test
    @DisplayName("never throws on null-ish event data fields")
    void neverThrowsOnPartialEvent() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ClickTracker tracker = new ClickTracker(propsWithCapacity(10),
                new NoopGeoEnricher(), registry);

        // ClickEventDto records carry an explicit data map; supply one with no entries
        // to exercise the "missing ip / referer / userAgent" path.
        ClickEventDto partial = new ClickEventDto(1L, Instant.now(), new HashMap<>());

        assertThatCode(() -> tracker.track(partial)).doesNotThrowAnyException();
        assertThat(tracker.queueDepth()).isEqualTo(1);
    }

    private static ClickEventDto sampleEvent(int i) {
        Map<String, Object> data = new HashMap<>();
        data.put("referer", "https://example.com");
        data.put("userAgent", "TestUA");
        data.put("ip", "10.0.0." + (i % 255));
        return new ClickEventDto(1L, Instant.now(), data);
    }
}

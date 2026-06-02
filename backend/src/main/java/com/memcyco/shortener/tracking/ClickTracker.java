package com.memcyco.shortener.tracking;

import com.memcyco.shortener.config.AppProperties;
import com.memcyco.shortener.shortlink.dto.ClickEventDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Fire-and-forget ingest for click events on the redirect hot path.
 * Backed by a bounded queue; drains performed by {@link ClickBatchWriter}.
 * Never blocks, never throws — overflow increments a Micrometer counter.
 */
@Component
public class ClickTracker {

    private static final Logger log = LoggerFactory.getLogger(ClickTracker.class);

    private final BlockingQueue<ClickEventDto> queue;
    private final GeoEnricher geoEnricher;
    private final Counter droppedCounter;
    private final Counter acceptedCounter;

    public ClickTracker(AppProperties props,
                        GeoEnricher geoEnricher,
                        MeterRegistry meterRegistry) {
        this.queue = new LinkedBlockingQueue<>(props.clickTracker().queueCapacity());
        this.geoEnricher = geoEnricher;
        this.droppedCounter = Counter.builder("memcyco_clicks_dropped_total")
                .description("Click events dropped because the in-process queue was full.")
                .register(meterRegistry);
        this.acceptedCounter = Counter.builder("memcyco_clicks_accepted_total")
                .description("Click events accepted onto the in-process queue.")
                .register(meterRegistry);
    }

    public void track(ClickEventDto event) {
        try {
            // Geo enrichment is best-effort and runs synchronously here so the
            // enriched fields are already on the event by the time it's drained.
            // For NoopGeoEnricher this is a no-op; for MaxMind it's an in-memory
            // mmdb lookup (microseconds), well within hot-path budget.
            Object ip = event.data().get("ip");
            geoEnricher.enrich(ip == null ? null : ip.toString(), event.data());

            if (queue.offer(event)) {
                acceptedCounter.increment();
            } else {
                droppedCounter.increment();
                log.warn("Click queue full — dropping event for shortLinkId={}", event.shortLinkId());
            }
        } catch (RuntimeException ex) {
            // Belt and braces — tracker must never propagate.
            droppedCounter.increment();
            log.warn("Click tracking failed — ignoring: {}", ex.getMessage());
        }
    }

    /** Drain up to {@code max} events into the supplied list. */
    int drainTo(List<ClickEventDto> sink, int max) {
        return queue.drainTo(sink, max);
    }

    /** Snapshot of current queue depth (for tests / metrics). */
    public int queueDepth() {
        return queue.size();
    }

    /** Drain everything remaining — used on shutdown by the batch writer. */
    List<ClickEventDto> drainAll() {
        List<ClickEventDto> all = new ArrayList<>(queue.size());
        queue.drainTo(all);
        return all;
    }
}

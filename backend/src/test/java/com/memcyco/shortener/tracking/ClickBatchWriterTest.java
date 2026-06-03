package com.memcyco.shortener.tracking;

import com.memcyco.shortener.config.AppProperties;
import com.memcyco.shortener.shortlink.dto.ClickEventDto;
import com.memcyco.shortener.shortlink.repo.ClickJdbcRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.memcyco.shortener.tracking.ClickBatchWriter;
import com.memcyco.shortener.shortlink.dto.ClickEventDto;

/**
 * Unit-level test for the batch drain logic. We feed events into a real ClickTracker
 * and verify the writer drains them into a single batched insert through the mocked
 * {@link ClickJdbcRepository}.
 *
 * <p>End-to-end correctness (clicks actually landing in Postgres) is covered by
 * {@code RedirectControllerIT}; this test pins the drain/batch behaviour.
 */
@ExtendWith(MockitoExtension.class)
class ClickBatchWriterTest {

    @Mock ClickJdbcRepository jdbcRepository;

    private static AppProperties props(int capacity, int batchSize) {
        return new AppProperties(
                "http://localhost:8080",
                new AppProperties.Cache(60, 30),
                new AppProperties.ClickTracker(capacity, batchSize, 1000),
                new AppProperties.RateLimit(false, 60),
                new AppProperties.Geo(false, null)
        );
    }

    /**
     * A real TransactionTemplate over a stub tx manager: it executes the callback
     * (so the batched writes still run and can be verified) without needing a DB.
     */
    private static TransactionTemplate directTx() {
        return new TransactionTemplate(mock(PlatformTransactionManager.class));
    }

    private static ClickEventDto sampleEvent() {
        Map<String, Object> data = new HashMap<>();
        data.put("referer", "https://r");
        data.put("userAgent", "ua");
        data.put("ip", "1.2.3.4");
        return new ClickEventDto(1L, Instant.now(), data);
    }

    @Test
    @DisplayName("drains queue into a batched INSERT via ClickJdbcRepository")
    void drainsIntoBatchInsert() {
        AppProperties properties = props(100, 100);
        ClickTracker tracker = new ClickTracker(properties,
                new NoopGeoEnricher(), new SimpleMeterRegistry());
        for (int i = 0; i < 7; i++) {
            tracker.track(sampleEvent());
        }

        ClickBatchWriter writer = new ClickBatchWriter(tracker, jdbcRepository, properties, directTx());
        writer.flush();

        ArgumentCaptor<List<ClickEventDto>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(jdbcRepository, times(1)).insertBatch(batchCaptor.capture());
        verify(jdbcRepository, times(1)).bumpClickCounts(anyMap());
        assert batchCaptor.getValue().size() == 7;
    }

    @Test
    @DisplayName("respects configured batchSize when draining — needs multiple flushes for 250 events")
    void respectsBatchSize() {
        AppProperties properties = props(500, 100);
        ClickTracker tracker = new ClickTracker(properties,
                new NoopGeoEnricher(), new SimpleMeterRegistry());
        for (int i = 0; i < 250; i++) {
            tracker.track(sampleEvent());
        }

        ClickBatchWriter writer = new ClickBatchWriter(tracker, jdbcRepository, properties, directTx());
        // The scheduler calls flush() repeatedly; each call drains up to batchSize.
        writer.flush();
        writer.flush();
        writer.flush();

        verify(jdbcRepository, times(3)).insertBatch(any(List.class));
    }

    @Test
    @DisplayName("empty queue is a no-op")
    void emptyQueueIsNoop() {
        AppProperties properties = props(100, 100);
        ClickTracker tracker = new ClickTracker(properties,
                new NoopGeoEnricher(), new SimpleMeterRegistry());

        ClickBatchWriter writer = new ClickBatchWriter(tracker, jdbcRepository, properties, directTx());
        writer.flush();

        verifyNoInteractions(jdbcRepository);
    }
}

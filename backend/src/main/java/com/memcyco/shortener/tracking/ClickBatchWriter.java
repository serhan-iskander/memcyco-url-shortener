package com.memcyco.shortener.tracking;

import com.memcyco.shortener.config.AppProperties;
import com.memcyco.shortener.shortlink.dto.ClickEventDto;
import com.memcyco.shortener.shortlink.repo.ClickJdbcRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drains the in-memory click queue and batch-inserts to Postgres. Also bumps
 * the persisted {@code click_count} so the next list query reflects recent traffic
 * (the cache holds the authoritative near-real-time value).
 */
@Component
@RequiredArgsConstructor
public class ClickBatchWriter {

    private static final Logger log = LoggerFactory.getLogger(ClickBatchWriter.class);

    private final ClickTracker tracker;
    private final ClickJdbcRepository jdbcRepository;
    private final AppProperties props;
    private final TransactionTemplate txTemplate;

    @Scheduled(fixedDelayString = "${app.click-tracker.flush-interval-ms}")
    public void flush() {
        List<ClickEventDto> batch = new ArrayList<>(props.clickTracker().batchSize());
        int drained = tracker.drainTo(batch, props.clickTracker().batchSize());
        if (drained > 0) {
            writeBatch(batch);
        }
    }

    @PreDestroy
    void drainOnShutdown() {
        List<ClickEventDto> remaining = tracker.drainAll();
        if (!remaining.isEmpty()) {
            log.info("Draining {} click events on shutdown.", remaining.size());
            try {
                writeBatch(remaining);
            } catch (RuntimeException ex) {
                log.warn("Final drain failed — events lost: {}", ex.getMessage());
            }
        }
    }

    private void writeBatch(List<ClickEventDto> batch) {
        try {
            // Real transaction boundary so the insert and the count bump commit
            // together or not at all. A method-level @Transactional here would be a
            // no-op: writeBatch is called from within this same bean (flush /
            // drainOnShutdown), so the Spring proxy is bypassed. TransactionTemplate
            // opens the transaction programmatically and isn't subject to that.
            txTemplate.executeWithoutResult(status -> {
                jdbcRepository.insertBatch(batch);
                Map<Long, Long> deltas = new HashMap<>();
                for (ClickEventDto e : batch) {
                    deltas.merge(e.shortLinkId(), 1L, Long::sum);
                }
                jdbcRepository.bumpClickCounts(deltas);
            });
        } catch (RuntimeException ex) {
            // Catch OUTSIDE the transaction: a failure rolls back cleanly, and we
            // don't propagate so one bad batch can't tear down the scheduler or the
            // shutdown drain.
            log.warn("Batched click write failed for {} events, dropped: {}",
                    batch.size(), ex.getMessage());
        }
    }
}

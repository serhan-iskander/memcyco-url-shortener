package com.memcyco.shortener.shortlink.service;

import com.memcyco.shortener.shortlink.cache.ShortLinkCache;
import com.memcyco.shortener.shortlink.event.ShortLinkChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Drops the Redis entry AFTER the DB transaction commits so we never
 * end up with the cache holding a stale value the DB has already replaced.
 */
@Component
@RequiredArgsConstructor
public class ShortLinkCacheInvalidationListener {

    private final ShortLinkCache cache;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChange(ShortLinkChanged event) {
        cache.invalidate(event.shortCode());
    }
}

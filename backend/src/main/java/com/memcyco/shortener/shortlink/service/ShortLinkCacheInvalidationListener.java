package com.memcyco.shortener.shortlink.service;

import com.memcyco.shortener.shortlink.cache.ShortLinkCache;
import com.memcyco.shortener.shortlink.event.ShortLinkChanged;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Drops the Redis entry AFTER the DB transaction commits so we never
 * end up with the cache holding a stale value the DB has already replaced.
 */
@Component
public class ShortLinkCacheInvalidationListener {

    private final ShortLinkCache cache;

    public ShortLinkCacheInvalidationListener(ShortLinkCache cache) {
        this.cache = cache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChange(ShortLinkChanged event) {
        cache.invalidate(event.shortCode());
    }
}

package com.memcyco.shortener.shortlink.domain;

/**
 * Derived (not persisted) status for a short link. Computed from
 * {@code active}, {@code expires_at}, and {@code click_count vs max_clicks}.
 */
public enum LinkStatus {
    ACTIVE,
    EXPIRED,
    EXHAUSTED,
    INACTIVE
}

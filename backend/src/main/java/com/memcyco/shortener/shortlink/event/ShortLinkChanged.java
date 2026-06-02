package com.memcyco.shortener.shortlink.event;

/**
 * Domain event published when a short link's persisted state has changed
 * (update or soft-delete). Listeners run AFTER_COMMIT and use it to drop
 * any cached representation of {@code shortCode}.
 */
public record ShortLinkChanged(String shortCode) {}

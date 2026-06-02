package com.memcyco.shortener.tracking;

import java.util.Map;

/**
 * Pluggable enrichment for click data. Implementations add fields to the
 * {@code data} map in place (e.g. country, city).
 *
 * <h2>Fast-path contract — load-bearing</h2>
 * {@link #enrich} is invoked <strong>synchronously on the redirect hot path</strong>
 * (see {@link ClickTracker#track}). Implementations MUST:
 * <ul>
 *   <li>Return within ~1 ms in the common case (in-memory lookups only).</li>
 *   <li>Never perform blocking I/O — no network, disk, or DB calls.</li>
 *   <li>Never throw — swallow internal errors and return without mutating {@code data}.</li>
 * </ul>
 * Need a remote enrichment source? Wrap it behind an async cache populated off-band,
 * then expose a synchronous in-memory lookup here. Violating this contract slows
 * every redirect served by the application.
 */
public interface GeoEnricher {
    /** Annotate {@code data} with whatever this enricher knows about {@code ip}. Must be fast and non-throwing — see class Javadoc. */
    void enrich(String ip, Map<String, Object> data);
}

package com.memcyco.shortener.strategy;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Context handed to a strategy for a single generation attempt.
 *
 * @param originalUrl   the destination URL (some strategies hash this)
 * @param parameters    strategy-specific parameters (pre-validated against schema)
 * @param collisionTest returns true if the candidate code already exists; strategies
 *                      may use this to retry within their own bounds.
 */
public record GenerationContext(
        String originalUrl,
        Map<String, Object> parameters,
        Predicate<String> collisionTest
) {
}

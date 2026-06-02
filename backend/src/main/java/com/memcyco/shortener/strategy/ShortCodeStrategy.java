package com.memcyco.shortener.strategy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy for generating a short code. Implementations are Spring beans
 * registered in {@link StrategyRegistry} keyed by {@link #name()}.
 */
public interface ShortCodeStrategy {

    /** Stable strategy identifier — used as the API enum value and persisted on the row. */
    String name();

    /** Human-friendly label for the UI. */
    String displayName();

    /** UI tooltip / description. */
    String description();

    /** Parameter schema; consumed by both backend validation and the dynamic UI form. */
    List<ParameterDescriptor> parameterSchema();

    /**
     * Hook for strategies that need to lift convenience top-level request fields
     * (e.g. CUSTOM_ALIAS's {@code alias}) into the parameters map before schema
     * validation. Default is a passthrough; the service never needs to know
     * which strategy is special.
     */
    default Map<String, Object> prepareParams(String topLevelAlias, Map<String, Object> params) {
        return params == null ? new HashMap<>() : new HashMap<>(params);
    }

    /**
     * Generate a short code. Implementations may use {@code ctx.collisionTest()}
     * to retry; if they exhaust retries they must throw a runtime exception.
     */
    String generate(GenerationContext ctx);
}

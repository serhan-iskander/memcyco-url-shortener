package com.memcyco.shortener.strategy;

import com.memcyco.shortener.common.error.InvalidStrategyException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Discovers all {@link ShortCodeStrategy} beans and exposes them keyed by name.
 * Read-only — strategies are code, not data.
 */
@Component
public class StrategyRegistry {

    private final Map<String, ShortCodeStrategy> byName;

    public StrategyRegistry(List<ShortCodeStrategy> strategies) {
        this.byName = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(ShortCodeStrategy::name, s -> s));
    }

    public ShortCodeStrategy require(String name) {
        ShortCodeStrategy s = byName.get(name);
        if (s == null) {
            throw new InvalidStrategyException("Unknown strategy: " + name
                    + ". Valid: " + byName.keySet());
        }
        return s;
    }

    public Collection<ShortCodeStrategy> all() {
        return byName.values();
    }

    public List<StrategyDescriptor> describe() {
        return byName.values().stream()
                .map(s -> new StrategyDescriptor(s.name(), s.displayName(),
                        s.description(), s.parameterSchema()))
                .sorted((a, b) -> a.name().compareTo(b.name()))
                .toList();
    }
}

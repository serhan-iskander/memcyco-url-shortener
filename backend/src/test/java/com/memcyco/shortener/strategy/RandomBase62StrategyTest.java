package com.memcyco.shortener.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

import com.memcyco.shortener.strategy.RandomBase62Strategy;
import com.memcyco.shortener.strategy.GenerationContext;

class RandomBase62StrategyTest {

    private static final String BASE62 = "[A-Za-z0-9]+";

    @Test
    @DisplayName("generates a code of the configured length using base62 alphabet")
    void generatesBase62CodeOfConfiguredLength() {
        RandomBase62Strategy strategy = new RandomBase62Strategy();
        GenerationContext req = new GenerationContext(
                "https://example.com",
                Map.of("length", 8),
                code -> false // no collisions
        );

        String code = strategy.generate(req);

        assertThat(code).hasSize(8).matches(BASE62);
    }

    @Test
    @DisplayName("defaults to 7 chars when length parameter omitted")
    void defaultsToSevenWhenLengthOmitted() {
        RandomBase62Strategy strategy = new RandomBase62Strategy();
        GenerationContext req = new GenerationContext(
                "https://example.com",
                Map.of(),
                code -> false
        );

        String code = strategy.generate(req);

        assertThat(code).hasSize(7).matches(BASE62);
    }

    @Test
    @DisplayName("retries when collision predicate signals an existing code")
    void retriesOnCollision() {
        // Predicate returns true (collision) for the first two attempts, then false.
        int[] calls = {0};
        Predicate<String> collisionThenSuccess = code -> {
            calls[0]++;
            return calls[0] <= 2;
        };

        RandomBase62Strategy strategy = new RandomBase62Strategy();
        String code = strategy.generate(new GenerationContext(
                "https://example.com",
                Map.of("length", 7),
                collisionThenSuccess
        ));

        assertThat(code).matches(BASE62);
        assertThat(calls[0]).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("produces high-cardinality output (no obvious repeats)")
    void producesDistinctCodes() {
        RandomBase62Strategy strategy = new RandomBase62Strategy();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(strategy.generate(new GenerationContext(
                    "https://example.com/" + i,
                    Map.of("length", 8),
                    code -> false
            )));
        }
        // Tiny chance of birthday collision at length 8 — should be effectively 200 unique.
        assertThat(seen).hasSizeGreaterThan(195);
    }

    @Test
    @DisplayName("strategy name matches the public enum identifier")
    void strategyNameIsRandomBase62() {
        assertThat(new RandomBase62Strategy().name()).isEqualTo("RANDOM_BASE62");
    }
}

package com.memcyco.shortener.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

import com.memcyco.shortener.strategy.HashTruncationStrategy;
import com.memcyco.shortener.strategy.GenerationContext;

class HashTruncationStrategyTest {

    @Test
    @DisplayName("deterministic for the same URL when no collision occurs")
    void deterministicForSameUrl() {
        HashTruncationStrategy strategy = new HashTruncationStrategy();
        Map<String, Object> params = Map.of("length", 7);

        String first  = strategy.generate(new GenerationContext(
                "https://example.com/article/42", params, c -> false));
        String second = strategy.generate(new GenerationContext(
                "https://example.com/article/42", params, c -> false));

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("different URLs produce different short codes")
    void differentUrlsProduceDifferentCodes() {
        HashTruncationStrategy strategy = new HashTruncationStrategy();
        Map<String, Object> params = Map.of("length", 7);

        String a = strategy.generate(new GenerationContext("https://example.com/a", params, c -> false));
        String b = strategy.generate(new GenerationContext("https://example.com/b", params, c -> false));

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("retry on collision lengthens or remixes until predicate accepts")
    void retryOnCollisionLengthens() {
        HashTruncationStrategy strategy = new HashTruncationStrategy();
        int[] attempts = {0};
        Predicate<String> firstTwoCollide = code -> {
            attempts[0]++;
            return attempts[0] <= 2;
        };

        String code = strategy.generate(new GenerationContext(
                "https://example.com/collision-prone",
                Map.of("length", 6),
                firstTwoCollide
        ));

        // The final accepted code must exist; how the impl avoids the collision
        // (lengthen, re-salt) is its choice — we only require it eventually settles.
        assertThat(code).isNotBlank();
        assertThat(attempts[0]).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("strategy name matches the public enum identifier")
    void strategyNameIsHashTrunc() {
        assertThat(new HashTruncationStrategy().name()).isEqualTo("HASH_TRUNC");
    }
}

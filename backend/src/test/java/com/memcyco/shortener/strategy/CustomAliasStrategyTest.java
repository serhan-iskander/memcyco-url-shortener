package com.memcyco.shortener.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memcyco.shortener.strategy.CustomAliasStrategy;
import com.memcyco.shortener.strategy.GenerationContext;
import com.memcyco.shortener.common.error.DuplicateAliasException;
import com.memcyco.shortener.common.error.ParameterValidationException;

class CustomAliasStrategyTest {

    @Test
    @DisplayName("returns the supplied alias verbatim when valid and unique")
    void passesThroughValidAlias() {
        CustomAliasStrategy strategy = new CustomAliasStrategy();

        String code = strategy.generate(new GenerationContext(
                "https://example.com",
                Map.of("alias", "my-alias_123"),
                c -> false
        ));

        assertThat(code).isEqualTo("my-alias_123");
    }

    @Test
    @DisplayName("rejects an alias that violates the regex")
    void rejectsInvalidAlias() {
        CustomAliasStrategy strategy = new CustomAliasStrategy();

        assertThatThrownBy(() -> strategy.generate(new GenerationContext(
                "https://example.com",
                Map.of("alias", "has spaces!"),
                c -> false
        ))).isInstanceOf(ParameterValidationException.class);
    }

    @Test
    @DisplayName("rejects an alias that is too short")
    void rejectsTooShortAlias() {
        CustomAliasStrategy strategy = new CustomAliasStrategy();

        assertThatThrownBy(() -> strategy.generate(new GenerationContext(
                "https://example.com",
                Map.of("alias", "ab"),
                c -> false
        ))).isInstanceOf(ParameterValidationException.class);
    }

    @Test
    @DisplayName("throws DuplicateAliasException when the predicate signals collision")
    void throwsOnDuplicateAlias() {
        CustomAliasStrategy strategy = new CustomAliasStrategy();

        assertThatThrownBy(() -> strategy.generate(new GenerationContext(
                "https://example.com",
                Map.of("alias", "taken_alias"),
                c -> true // predicate: this code already exists
        ))).isInstanceOf(DuplicateAliasException.class);
    }

    @Test
    @DisplayName("strategy name matches the public enum identifier")
    void strategyNameIsCustomAlias() {
        assertThat(new CustomAliasStrategy().name()).isEqualTo("CUSTOM_ALIAS");
    }
}

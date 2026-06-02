package com.memcyco.shortener.strategy;

import com.memcyco.shortener.common.error.InvalidStrategyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.memcyco.shortener.strategy.StrategyRegistry;
import com.memcyco.shortener.strategy.ShortCodeStrategy;

class StrategyRegistryTest {

    @Test
    @DisplayName("returns the strategy registered for a given name")
    void returnsRegisteredStrategy() {
        ShortCodeStrategy s1 = Mockito.mock(ShortCodeStrategy.class);
        when(s1.name()).thenReturn("RANDOM_BASE62");
        ShortCodeStrategy s2 = Mockito.mock(ShortCodeStrategy.class);
        when(s2.name()).thenReturn("CUSTOM_ALIAS");

        StrategyRegistry registry = new StrategyRegistry(List.of(s1, s2));

        assertThat(registry.require("RANDOM_BASE62")).isSameAs(s1);
        assertThat(registry.require("CUSTOM_ALIAS")).isSameAs(s2);
    }

    @Test
    @DisplayName("lists every registered strategy")
    void listsAllStrategies() {
        ShortCodeStrategy s1 = Mockito.mock(ShortCodeStrategy.class);
        when(s1.name()).thenReturn("RANDOM_BASE62");
        ShortCodeStrategy s2 = Mockito.mock(ShortCodeStrategy.class);
        when(s2.name()).thenReturn("HASH_TRUNC");

        StrategyRegistry registry = new StrategyRegistry(List.of(s1, s2));

        assertThat(registry.all())
                .extracting(ShortCodeStrategy::name)
                .containsExactlyInAnyOrder("RANDOM_BASE62", "HASH_TRUNC");
    }

    @Test
    @DisplayName("unknown strategy name surfaces a clear error")
    void unknownNameThrows() {
        ShortCodeStrategy s1 = Mockito.mock(ShortCodeStrategy.class);
        when(s1.name()).thenReturn("RANDOM_BASE62");

        StrategyRegistry registry = new StrategyRegistry(List.of(s1));

        assertThatThrownBy(() -> registry.require("DOES_NOT_EXIST"))
                .isInstanceOf(InvalidStrategyException.class);
    }
}

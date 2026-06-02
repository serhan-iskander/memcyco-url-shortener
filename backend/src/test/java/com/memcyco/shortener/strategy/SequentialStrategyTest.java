package com.memcyco.shortener.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.memcyco.shortener.strategy.SequentialStrategy;
import com.memcyco.shortener.strategy.GenerationContext;

class SequentialStrategyTest {

    private static final String BASE62 = "[A-Za-z0-9]+";

    @Test
    @DisplayName("encodes a sequential long into base62")
    void encodesSequenceToBase62() {
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(1L, 2L, 62L, 12345L);

        SequentialStrategy strategy = new SequentialStrategy(jdbc);

        String c1 = strategy.generate(new GenerationContext("https://x", Map.of(), c -> false));
        String c2 = strategy.generate(new GenerationContext("https://x", Map.of(), c -> false));
        String c3 = strategy.generate(new GenerationContext("https://x", Map.of(), c -> false));
        String c4 = strategy.generate(new GenerationContext("https://x", Map.of(), c -> false));

        assertThat(c1).matches(BASE62);
        assertThat(c2).matches(BASE62);
        assertThat(c3).matches(BASE62);
        assertThat(c4).matches(BASE62);
        assertThat(c1).isNotEqualTo(c2);
        assertThat(c3).isNotEqualTo(c4);
    }

    @Test
    @DisplayName("strategy name matches the public enum identifier")
    void strategyNameIsSequential() {
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        assertThat(new SequentialStrategy(jdbc).name()).isEqualTo("SEQUENTIAL");
    }
}

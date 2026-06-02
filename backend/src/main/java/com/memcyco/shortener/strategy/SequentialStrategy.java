package com.memcyco.shortener.strategy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SequentialStrategy implements ShortCodeStrategy {

    static final String NAME = "SEQUENTIAL";
    /** See V2__sequential_strategy_seq.sql. */
    private static final String NEXT_VAL_SQL = "SELECT nextval('seq_short_code_counter')";

    private final JdbcTemplate jdbc;

    public SequentialStrategy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() { return NAME; }

    @Override
    public String displayName() { return "Sequential (base62-encoded counter)"; }

    @Override
    public String description() {
        return "Reads the next value from a dedicated Postgres sequence and base62-encodes it. "
                + "Codes are short, monotonically increasing, and never collide.";
    }

    @Override
    public List<ParameterDescriptor> parameterSchema() {
        return List.of();
    }

    @Override
    public String generate(GenerationContext ctx) {
        Long n = jdbc.queryForObject(NEXT_VAL_SQL, Long.class);
        if (n == null) {
            throw new IllegalStateException("nextval returned null");
        }
        return Base62.encode(n);
    }
}

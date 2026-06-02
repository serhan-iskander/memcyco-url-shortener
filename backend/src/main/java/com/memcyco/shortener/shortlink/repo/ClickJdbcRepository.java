package com.memcyco.shortener.shortlink.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memcyco.shortener.shortlink.dto.ClickEventDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Map;

/**
 * Batched JDBC writes used by {@code ClickBatchWriter}. Bypasses JPA on the
 * hot path so we don't pay the cost of the persistence context for an
 * append-only event stream.
 */
@Repository
@RequiredArgsConstructor
public class ClickJdbcRepository {

    private static final String INSERT_SQL =
            "INSERT INTO clicks (short_link_id, clicked_at, data) VALUES (?, ?, ?::jsonb)";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public int[] insertBatch(List<ClickEventDto> events) {
        if (events.isEmpty()) {
            return new int[0];
        }
        return jdbc.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ClickEventDto e = events.get(i);
                ps.setLong(1, e.shortLinkId());
                ps.setTimestamp(2, Timestamp.from(e.clickedAt()));
                ps.setString(3, writeJson(e.data()));
            }

            @Override
            public int getBatchSize() { return events.size(); }
        });
    }

    /** Bulk-bump click_count using a single UPDATE per affected short_link_id. */
    public void bumpClickCounts(Map<Long, Long> deltasById) {
        if (deltasById.isEmpty()) {
            return;
        }
        List<Map.Entry<Long, Long>> entries = List.copyOf(deltasById.entrySet());
        jdbc.batchUpdate(
                "UPDATE short_links SET click_count = click_count + ? WHERE id = ?",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        Map.Entry<Long, Long> entry = entries.get(i);
                        ps.setLong(1, entry.getValue());
                        ps.setLong(2, entry.getKey());
                    }

                    @Override
                    public int getBatchSize() { return entries.size(); }

                    @SuppressWarnings("unused")
                    public int[] getSqlTypes() {
                        return new int[]{Types.BIGINT, Types.BIGINT};
                    }
                });
    }

    private String writeJson(Map<String, Object> data) {
        try {
            return mapper.writeValueAsString(data == null ? Map.of() : data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise click data", e);
        }
    }
}

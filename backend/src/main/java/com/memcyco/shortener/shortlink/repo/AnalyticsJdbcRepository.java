package com.memcyco.shortener.shortlink.repo;

import com.memcyco.shortener.shortlink.dto.AnalyticsResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Analytics aggregation queries. JdbcTemplate so we can use Postgres-specific
 * {@code date_trunc} / {@code ->>} operators and arrange the SQL exactly.
 */
@Repository
public class AnalyticsJdbcRepository {

    private static final Set<String> ALLOWED_BUCKETS =
            Set.of("minute", "hour", "day", "week", "month");

    private final JdbcTemplate jdbc;

    public AnalyticsJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AnalyticsResponse.BucketPoint> timeSeries(Long shortLinkId, String bucket,
                                                           Instant from, Instant to) {
        String b = normalizeBucket(bucket);
        // Bucket is enum-validated → safe to interpolate. From/to come from the user
        // but are bound as parameters.
        String sql = "SELECT date_trunc('" + b + "', clicked_at) AS bucket, COUNT(*) AS count "
                + "FROM clicks "
                + "WHERE short_link_id = ? AND clicked_at >= ? AND clicked_at < ? "
                + "GROUP BY bucket ORDER BY bucket";
        return jdbc.query(sql,
                ps -> {
                    ps.setLong(1, shortLinkId);
                    ps.setTimestamp(2, Timestamp.from(from));
                    ps.setTimestamp(3, Timestamp.from(to));
                },
                (rs, i) -> new AnalyticsResponse.BucketPoint(
                        rs.getTimestamp("bucket").toInstant(),
                        rs.getLong("count")));
    }

    public List<AnalyticsResponse.ValueCount> topByJsonField(Long shortLinkId, String jsonField, int limit) {
        // jsonField is a controlled enum from the service — validated there.
        String sql = "SELECT data->>? AS value, COUNT(*) AS count FROM clicks "
                + "WHERE short_link_id = ? AND data ? ? "
                + "GROUP BY 1 ORDER BY 2 DESC LIMIT ?";
        // The "?" in the WHERE clause is the JSON containment operator and conflicts
        // with positional binding; switch to a hand-rolled SQL that uses ->> twice.
        String safeSql = "SELECT data->>'" + jsonField + "' AS value, COUNT(*) AS count "
                + "FROM clicks WHERE short_link_id = ? AND data->>'" + jsonField + "' IS NOT NULL "
                + "GROUP BY 1 ORDER BY 2 DESC LIMIT ?";
        return jdbc.query(safeSql,
                ps -> {
                    ps.setLong(1, shortLinkId);
                    ps.setInt(2, limit);
                },
                (rs, i) -> new AnalyticsResponse.ValueCount(
                        rs.getString("value"),
                        rs.getLong("count")));
    }

    public long totalClicks(Long shortLinkId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM clicks WHERE short_link_id = ?",
                Long.class, shortLinkId);
        return n == null ? 0L : n;
    }

    public long clicksSince(Long shortLinkId, Instant since) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM clicks WHERE short_link_id = ? AND clicked_at >= ?",
                Long.class, shortLinkId, Timestamp.from(since));
        return n == null ? 0L : n;
    }

    public long uniqueReferers(Long shortLinkId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT data->>'referer') FROM clicks "
                        + "WHERE short_link_id = ? AND data->>'referer' IS NOT NULL",
                Long.class, shortLinkId);
        return n == null ? 0L : n;
    }

    private String normalizeBucket(String bucket) {
        String b = bucket == null ? "hour" : bucket.toLowerCase();
        if (!ALLOWED_BUCKETS.contains(b)) {
            throw new IllegalArgumentException("Unsupported bucket: " + bucket
                    + " (allowed: " + ALLOWED_BUCKETS + ")");
        }
        return b;
    }
}

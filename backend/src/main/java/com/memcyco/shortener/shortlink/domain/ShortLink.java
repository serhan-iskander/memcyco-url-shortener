package com.memcyco.shortener.shortlink.domain;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.Type;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "short_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("deleted_at IS NULL")
public class ShortLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, length = 32)
    private String shortCode;

    @Column(name = "original_url", nullable = false, columnDefinition = "text")
    private String originalUrl;

    @Column(name = "strategy", nullable = false, length = 32)
    private String strategy;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "max_clicks")
    private Long maxClicks;

    @Column(name = "click_count", nullable = false)
    @Builder.Default
    private Long clickCount = 0L;

    @Type(StringArrayType.class)
    @Column(name = "tags", columnDefinition = "text[]", nullable = false)
    @Builder.Default
    private String[] tags = new String[0];

    @Type(JsonBinaryType.class)
    @Column(name = "parameters", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now(Clock.systemUTC());
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        // The DB trigger also sets updated_at; this keeps the in-memory entity in sync.
        updatedAt = Instant.now(Clock.systemUTC());
    }
}

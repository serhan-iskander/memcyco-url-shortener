package com.memcyco.shortener.shortlink.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "clicks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Click {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_link_id", nullable = false)
    private Long shortLinkId;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    @Type(JsonBinaryType.class)
    @Column(name = "data", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();
}

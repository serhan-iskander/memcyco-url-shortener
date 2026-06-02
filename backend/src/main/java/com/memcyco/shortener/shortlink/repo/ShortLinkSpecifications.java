package com.memcyco.shortener.shortlink.repo;

import com.memcyco.shortener.shortlink.domain.ShortLink;
import jakarta.persistence.criteria.Expression;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;

/**
 * JPA Specifications for the list endpoint. Status filter is intentionally
 * applied in-app (see {@code ShortLinkService}) because the status is derived
 * from multiple columns and a timestamp comparison vs now().
 */
public final class ShortLinkSpecifications {

    private ShortLinkSpecifications() {}

    /** Tag filter: matches if the requested tag is an element of the {@code tags} array. */
    public static Specification<ShortLink> hasTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        return (root, query, cb) -> {
            // Postgres tags TEXT[] — use Hibernate's array_contains via SQL function call.
            // Cleanest portable form: native SQL fragment via cb.function with the array literal.
            // We can't easily use the @> operator through Criteria, so fall back to ANY().
            Expression<String[]> tagsExpr = root.get("tags");
            return cb.isTrue(cb.function("array_position", Integer.class,
                    tagsExpr, cb.literal(tag)).isNotNull());
        };
    }

    /** Always-true safety spec — used as the base when no filters are supplied. */
    public static Specification<ShortLink> any() {
        return (root, query, cb) -> cb.conjunction();
    }

    /** Composes specs filtering out nulls. */
    public static Specification<ShortLink> compose(Specification<ShortLink>... specs) {
        Specification<ShortLink> result = any();
        for (Specification<ShortLink> s : Arrays.stream(specs).filter(java.util.Objects::nonNull).toList()) {
            result = result.and(s);
        }
        return result;
    }
}

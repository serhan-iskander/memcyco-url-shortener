package com.memcyco.shortener.shortlink.repo;

import com.memcyco.shortener.shortlink.domain.ShortLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface ShortLinkRepository
        extends JpaRepository<ShortLink, Long>, JpaSpecificationExecutor<ShortLink> {

    Optional<ShortLink> findByShortCode(String shortCode);

    /**
     * Soft delete — sets deleted_at and flips active to false. Bypasses the
     * @SQLRestriction filter via native SQL so we can update the soon-to-be-hidden row.
     */
    @Modifying
    @Query(value = "UPDATE short_links SET deleted_at = :now, active = false "
            + "WHERE id = :id AND deleted_at IS NULL", nativeQuery = true)
    int softDelete(@Param("id") Long id, @Param("now") Instant now);
}

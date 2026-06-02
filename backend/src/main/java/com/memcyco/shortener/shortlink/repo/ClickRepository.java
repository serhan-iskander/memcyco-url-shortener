package com.memcyco.shortener.shortlink.repo;

import com.memcyco.shortener.shortlink.domain.Click;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClickRepository extends JpaRepository<Click, Long> {
    long countByShortLinkId(Long shortLinkId);
}

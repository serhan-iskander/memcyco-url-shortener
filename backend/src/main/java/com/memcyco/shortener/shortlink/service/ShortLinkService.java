package com.memcyco.shortener.shortlink.service;

import com.memcyco.shortener.common.error.DuplicateAliasException;
import com.memcyco.shortener.common.error.ParameterValidationException;
import com.memcyco.shortener.common.error.ShortLinkNotFoundException;
import com.memcyco.shortener.shortlink.domain.LinkStatus;
import com.memcyco.shortener.shortlink.domain.ShortLink;
import com.memcyco.shortener.shortlink.dto.CreateShortLinkRequest;
import com.memcyco.shortener.shortlink.dto.ShortLinkListResponse;
import com.memcyco.shortener.shortlink.dto.ShortLinkResponse;
import com.memcyco.shortener.shortlink.dto.UpdateShortLinkRequest;
import com.memcyco.shortener.shortlink.event.ShortLinkChanged;
import com.memcyco.shortener.shortlink.repo.ShortLinkRepository;
import com.memcyco.shortener.shortlink.repo.ShortLinkSpecifications;
import com.memcyco.shortener.strategy.GenerationContext;
import com.memcyco.shortener.strategy.ParameterSchemaValidator;
import com.memcyco.shortener.strategy.ShortCodeStrategy;
import com.memcyco.shortener.strategy.StrategyRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class ShortLinkService {

    private static final int MAX_PAGE_SIZE = 100;
    /** Name of the partial-unique index that gates short_code uniqueness; see V1__init.sql. */
    static final String SHORT_CODE_UNIQUE_INDEX = "short_links_code_live_uq";

    private final ShortLinkRepository repo;
    private final StrategyRegistry strategies;
    private final ParameterSchemaValidator paramValidator;
    private final ShortLinkMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ShortLinkResponse create(CreateShortLinkRequest req) {
        validateExpiresFuture(req.expiresAt());
        ShortCodeStrategy strategy = strategies.require(req.strategy());

        // Strategy decides whether/how to lift convenience top-level fields (e.g. CUSTOM_ALIAS's
        // `alias`) into the parameters map. Default impl is a passthrough.
        Map<String, Object> params = strategy.prepareParams(req.alias(), req.parameters());
        Map<String, Object> validated = paramValidator.validate(strategy, params);

        GenerationContext ctx = new GenerationContext(req.originalUrl(), validated,
                code -> repo.findByShortCode(code).isPresent());
        String code = strategy.generate(ctx);

        ShortLink entity = ShortLink.builder()
                .shortCode(code)
                .originalUrl(req.originalUrl())
                .strategy(strategy.name())
                .expiresAt(req.expiresAt())
                .maxClicks(req.maxClicks())
                .tags(req.tags() == null ? new String[0] : req.tags().toArray(new String[0]))
                .parameters(validated)
                .active(true)
                .build();
        try {
            ShortLink saved = repo.saveAndFlush(entity);
            return mapper.toResponse(saved);
        } catch (DataIntegrityViolationException dup) {
            String msg = dup.getMostSpecificCause().getMessage();
            if (msg != null && msg.contains(SHORT_CODE_UNIQUE_INDEX)) {
                throw new DuplicateAliasException(code);
            }
            throw dup;
        }
    }

    @Transactional(readOnly = true)
    public ShortLinkResponse get(Long id) {
        return mapper.toResponse(loadOrThrow(id));
    }

    public ShortLinkResponse update(Long id, UpdateShortLinkRequest req) {
        ShortLink sl = loadOrThrow(id);
        if (req.originalUrl() != null) {
            sl.setOriginalUrl(req.originalUrl());
        }
        if (req.expiresAt() != null) {
            sl.setExpiresAt(req.expiresAt()); // updates may set past expirations explicitly
        }
        if (req.maxClicks() != null) {
            sl.setMaxClicks(req.maxClicks());
        }
        if (req.tags() != null) {
            sl.setTags(req.tags().toArray(new String[0]));
        }
        if (req.active() != null) {
            sl.setActive(req.active());
        }
        ShortLink saved = repo.save(sl);
        events.publishEvent(new ShortLinkChanged(saved.getShortCode()));
        return mapper.toResponse(saved);
    }

    public void softDelete(Long id) {
        ShortLink sl = loadOrThrow(id);
        int rows = repo.softDelete(id, Instant.now(clock));
        if (rows == 0) {
            throw new ShortLinkNotFoundException("short link " + id);
        }
        events.publishEvent(new ShortLinkChanged(sl.getShortCode()));
    }

    @Transactional(readOnly = true)
    public ShortLinkListResponse list(int page, int size, String tag, LinkStatus statusFilter) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        PageRequest pr = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ShortLink> result = repo.findAll(
                ShortLinkSpecifications.compose(ShortLinkSpecifications.hasTag(tag)), pr);

        List<ShortLinkResponse> items = result.getContent().stream()
                .map(mapper::toResponse)
                .filter(r -> statusFilter == null || r.status() == statusFilter)
                .toList();

        // total is the unfiltered count from the page; for status filter the count
        // would require enumerating all rows — acceptable trade-off for derived status.
        long total = statusFilter == null ? result.getTotalElements() : items.size();
        return new ShortLinkListResponse(items, safePage, safeSize, total);
    }

    public ShortLink loadOrThrow(Long id) {
        return repo.findById(id).orElseThrow(() ->
                new ShortLinkNotFoundException("short link " + id));
    }

    private void validateExpiresFuture(Instant expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(Instant.now(clock))) {
            throw new ParameterValidationException("expiresAt must be in the future",
                    List.of(Map.of("field", "expiresAt", "message", "must be in the future")));
        }
    }
}

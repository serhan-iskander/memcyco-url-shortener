package com.memcyco.shortener.shortlink.api;

import com.memcyco.shortener.shortlink.domain.LinkStatus;
import com.memcyco.shortener.shortlink.dto.CreateShortLinkRequest;
import com.memcyco.shortener.shortlink.dto.ShortLinkListResponse;
import com.memcyco.shortener.shortlink.dto.ShortLinkResponse;
import com.memcyco.shortener.shortlink.dto.UpdateShortLinkRequest;
import com.memcyco.shortener.shortlink.service.QrService;
import com.memcyco.shortener.shortlink.service.ShortLinkMapper;
import com.memcyco.shortener.shortlink.service.ShortLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/short-links")
@RequiredArgsConstructor
@Tag(name = "Short links", description = "CRUD + QR for short links")
public class ShortLinkController {

    private final ShortLinkService service;
    private final QrService qrService;
    private final ShortLinkMapper mapper;

    @PostMapping
    @Operation(summary = "Create a short link")
    public ResponseEntity<ShortLinkResponse> create(@Valid @RequestBody CreateShortLinkRequest req) {
        ShortLinkResponse created = service.create(req);
        return ResponseEntity.created(URI.create("/api/short-links/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "List short links with pagination and optional tag / status filters")
    public ShortLinkListResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) LinkStatus status
    ) {
        return service.list(page, size, tag, status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a short link by id")
    public ShortLinkResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a short link (PATCH-style merge; immutable fields ignored)")
    public ShortLinkResponse update(@PathVariable Long id, @Valid @RequestBody UpdateShortLinkRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a short link")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Generate a PNG QR code for the short URL")
    public ResponseEntity<byte[]> qr(@PathVariable Long id,
                                     @RequestParam(defaultValue = "256") int size) {
        ShortLinkResponse sl = service.get(id);
        byte[] png = qrService.pngFor(mapper.buildShortUrl(sl.shortCode()), size);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .body(png);
    }
}

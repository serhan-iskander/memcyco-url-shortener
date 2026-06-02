package com.memcyco.shortener.shortlink.dto;

import java.util.List;

public record ShortLinkListResponse(
        List<ShortLinkResponse> items,
        int page,
        int size,
        long total
) {
}

package com.memcyco.shortener.common.error;

import com.memcyco.shortener.shortlink.domain.LinkStatus;
import lombok.Getter;

@Getter
public class ShortLinkGoneException extends RuntimeException {
    private final LinkStatus status;

    public ShortLinkGoneException(String message, LinkStatus status) {
        super(message);
        this.status = status;
    }
}

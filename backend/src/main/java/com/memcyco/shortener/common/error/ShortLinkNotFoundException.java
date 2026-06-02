package com.memcyco.shortener.common.error;

public class ShortLinkNotFoundException extends RuntimeException {
    public ShortLinkNotFoundException(String message) {
        super(message);
    }
}

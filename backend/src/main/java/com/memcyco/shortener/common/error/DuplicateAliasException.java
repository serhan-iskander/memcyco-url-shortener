package com.memcyco.shortener.common.error;

public class DuplicateAliasException extends RuntimeException {
    public DuplicateAliasException(String alias) {
        super("Short code '" + alias + "' is already in use.");
    }
}

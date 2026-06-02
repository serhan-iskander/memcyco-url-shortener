package com.memcyco.shortener.common.error;

public class InvalidStrategyException extends RuntimeException {
    public InvalidStrategyException(String message) {
        super(message);
    }
}

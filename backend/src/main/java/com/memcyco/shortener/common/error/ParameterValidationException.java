package com.memcyco.shortener.common.error;

import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Thrown by strategy parameter schema validation. Carries per-field messages
 * surfaced through the RFC 7807 ProblemDetail "errors" array.
 */
@Getter
public class ParameterValidationException extends RuntimeException {
    private final List<Map<String, String>> fieldErrors;

    public ParameterValidationException(String message, List<Map<String, String>> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors;
    }
}

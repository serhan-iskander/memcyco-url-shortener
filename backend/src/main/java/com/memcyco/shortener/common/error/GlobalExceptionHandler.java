package com.memcyco.shortener.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_BASE = "https://memcyco.dev/errors/";

    @ExceptionHandler(ShortLinkNotFoundException.class)
    public ProblemDetail handleNotFound(ShortLinkNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Short link not found");
        pd.setType(URI.create(ERROR_BASE + "not-found"));
        return pd;
    }

    @ExceptionHandler(ShortLinkGoneException.class)
    public ProblemDetail handleGone(ShortLinkGoneException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
        pd.setTitle("Short link gone");
        pd.setType(URI.create(ERROR_BASE + "gone"));
        pd.setProperty("linkStatus", ex.getStatus().name());
        return pd;
    }

    @ExceptionHandler(DuplicateAliasException.class)
    public ProblemDetail handleDuplicateAlias(DuplicateAliasException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Duplicate alias");
        pd.setType(URI.create(ERROR_BASE + "duplicate-alias"));
        pd.setProperty("errors", List.of(Map.of("field", "alias", "message", "Already in use")));
        return pd;
    }

    @ExceptionHandler(InvalidStrategyException.class)
    public ProblemDetail handleInvalidStrategy(InvalidStrategyException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Invalid strategy");
        pd.setType(URI.create(ERROR_BASE + "invalid-strategy"));
        return pd;
    }

    @ExceptionHandler(ParameterValidationException.class)
    public ProblemDetail handleParameterValidation(ParameterValidationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Invalid strategy parameters");
        pd.setType(URI.create(ERROR_BASE + "invalid-parameters"));
        pd.setProperty("errors", ex.getFieldErrors());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                errors.add(Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()
                )));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Request validation failed");
        pd.setTitle("Validation failed");
        pd.setType(URI.create(ERROR_BASE + "validation"));
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        // Most commonly the partial unique index on short_code.
        String msg = ex.getMostSpecificCause().getMessage();
        if (msg != null && msg.contains("short_links_code_live_uq")) {
            return handleDuplicateAlias(new DuplicateAliasException("(conflicting short code)"));
        }
        log.warn("Data integrity violation: {}", msg);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Data integrity violation");
        pd.setTitle("Conflict");
        pd.setType(URI.create(ERROR_BASE + "conflict"));
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Bad request");
        pd.setType(URI.create(ERROR_BASE + "bad-request"));
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleFallback(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
        pd.setTitle("Internal server error");
        pd.setType(URI.create(ERROR_BASE + "internal"));
        return pd;
    }
}

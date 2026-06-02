package com.memcyco.shortener.common.error;

import com.memcyco.shortener.shortlink.domain.LinkStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import com.memcyco.shortener.common.error.GlobalExceptionHandler;
import com.memcyco.shortener.common.error.ShortLinkNotFoundException;
import com.memcyco.shortener.common.error.ShortLinkGoneException;
import com.memcyco.shortener.common.error.DuplicateAliasException;
import com.memcyco.shortener.common.error.ParameterValidationException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("ShortLinkNotFoundException → 404 ProblemDetail")
    void notFoundMapsTo404() {
        ProblemDetail pd = handler.handleNotFound(new ShortLinkNotFoundException("abc123"));

        assertThat(pd).isNotNull();
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(pd.getType().toString()).contains("not-found");
    }

    @Test
    @DisplayName("ShortLinkGoneException → 410 ProblemDetail")
    void goneMapsTo410() {
        ProblemDetail pd = handler.handleGone(new ShortLinkGoneException("abc123", LinkStatus.EXPIRED));

        assertThat(pd).isNotNull();
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.GONE.value());
        assertThat(pd.getType().toString()).contains("gone");
        assertThat(pd.getProperties()).containsEntry("linkStatus", "EXPIRED");
    }

    @Test
    @DisplayName("DuplicateAliasException → 409 ProblemDetail with the right type URI")
    void duplicateAliasMapsTo409() {
        ProblemDetail pd = handler.handleDuplicateAlias(new DuplicateAliasException("myalias"));

        assertThat(pd).isNotNull();
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getType().toString()).contains("duplicate-alias");
    }

    @Test
    @DisplayName("ParameterValidationException → 400 ProblemDetail with field errors")
    void validationMapsTo400() {
        ParameterValidationException ex = new ParameterValidationException("Invalid request",
                List.of(Map.of("field", "alias", "message", "must not be blank")));

        ProblemDetail pd = handler.handleParameterValidation(ex);

        assertThat(pd).isNotNull();
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getProperties()).containsKey("errors");
    }
}

package com.memcyco.shortener.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memcyco.shortener.strategy.ParameterSchemaValidator;
import com.memcyco.shortener.strategy.ParameterDescriptor;
import com.memcyco.shortener.common.error.ParameterValidationException;

class ParameterSchemaValidatorTest {

    private final ParameterSchemaValidator validator = new ParameterSchemaValidator();

    /** Tiny fake strategy that just exposes a static parameter schema. */
    private static ShortCodeStrategy strategyWith(List<ParameterDescriptor> schema) {
        return new ShortCodeStrategy() {
            @Override public String name() { return "FAKE"; }
            @Override public String displayName() { return "fake"; }
            @Override public String description() { return "fake"; }
            @Override public List<ParameterDescriptor> parameterSchema() { return schema; }
            @Override public String generate(GenerationContext ctx) { return "x"; }
        };
    }

    @Test
    @DisplayName("missing required parameter raises ParameterValidationException")
    void missingRequiredParameter() {
        ShortCodeStrategy strategy = strategyWith(List.of(
                ParameterDescriptor.stringParam("alias", true, null, null, "desc")
        ));

        assertThatThrownBy(() -> validator.validate(strategy, Map.of()))
                .isInstanceOf(ParameterValidationException.class)
                .hasMessageContaining("Invalid");
    }

    @Test
    @DisplayName("supplying the wrong type fails")
    void wrongTypeFails() {
        ShortCodeStrategy strategy = strategyWith(List.of(
                ParameterDescriptor.numberParam("length", false, 7, 4, 16, "desc")
        ));

        assertThatThrownBy(() -> validator.validate(strategy, Map.of("length", "not-a-number")))
                .isInstanceOf(ParameterValidationException.class);
    }

    @Test
    @DisplayName("numeric bounds are enforced")
    void numericBoundsEnforced() {
        ShortCodeStrategy strategy = strategyWith(List.of(
                ParameterDescriptor.numberParam("length", false, 7, 4, 16, "desc")
        ));

        assertThatThrownBy(() -> validator.validate(strategy, Map.of("length", 2)))
                .isInstanceOf(ParameterValidationException.class);
        assertThatThrownBy(() -> validator.validate(strategy, Map.of("length", 100)))
                .isInstanceOf(ParameterValidationException.class);
    }

    @Test
    @DisplayName("valid input passes")
    void validInputPasses() {
        ShortCodeStrategy strategy = strategyWith(List.of(
                ParameterDescriptor.numberParam("length", false, 7, 4, 16, "desc"),
                ParameterDescriptor.stringParam("alias", true, null, null, "desc")
        ));

        assertThatCode(() -> validator.validate(strategy,
                Map.of("length", 8, "alias", "myalias")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("optional parameter omitted is OK")
    void optionalOmittedOk() {
        ShortCodeStrategy strategy = strategyWith(List.of(
                ParameterDescriptor.numberParam("length", false, 7, 4, 16, "desc")
        ));

        assertThatCode(() -> validator.validate(strategy, Map.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("boolean type accepts true/false")
    void booleanTypeAccepted() {
        ShortCodeStrategy strategy = strategyWith(List.of(
                new ParameterDescriptor("preserveCase", ParameterDescriptor.ParamType.BOOLEAN,
                        false, false, null, null, null, "desc")
        ));

        assertThatCode(() -> validator.validate(strategy, Map.of("preserveCase", true)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(strategy, Map.of("preserveCase", "yes")))
                .isInstanceOf(ParameterValidationException.class);
    }

    @Test
    @DisplayName("schema with extra incoming params is currently lenient (not strict)")
    void extraParamsAreLenient() {
        // PHASE2-NOTE: Agent A's validator is lenient on unknown params — only declared
        // params are checked. If strict mode is needed later, that's a Phase 3 follow-up.
        ShortCodeStrategy strategy = strategyWith(List.of(
                ParameterDescriptor.numberParam("length", false, 7, 4, 16, "desc")
        ));

        assertThatCode(() -> validator.validate(strategy, Map.of("length", 8, "unknown", "ignored")))
                .doesNotThrowAnyException();
        // sanity
        assertThat(strategy.parameterSchema()).isNotEmpty();
    }
}

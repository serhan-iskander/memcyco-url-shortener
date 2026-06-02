package com.memcyco.shortener.strategy;

import com.memcyco.shortener.common.error.ParameterValidationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates a parameters map against a strategy's {@link ParameterDescriptor} list.
 * Checks: required-present, type, numeric bounds. Throws
 * {@link ParameterValidationException} (400) with per-field errors.
 */
@Component
public class ParameterSchemaValidator {

    public Map<String, Object> validate(ShortCodeStrategy strategy, Map<String, Object> incoming) {
        Map<String, Object> normalized = incoming == null ? new HashMap<>() : new HashMap<>(incoming);
        List<Map<String, String>> errors = new ArrayList<>();

        for (ParameterDescriptor desc : strategy.parameterSchema()) {
            Object value = normalized.get(desc.name());
            if (value == null) {
                if (desc.required()) {
                    errors.add(Map.of("field", "parameters." + desc.name(),
                            "message", "is required"));
                } else if (desc.defaultValue() != null) {
                    normalized.put(desc.name(), desc.defaultValue());
                }
                continue;
            }
            checkType(desc, value, errors);
            checkBounds(desc, value, errors);
        }

        if (!errors.isEmpty()) {
            throw new ParameterValidationException("Invalid strategy parameters", errors);
        }
        return normalized;
    }

    private void checkType(ParameterDescriptor desc, Object value, List<Map<String, String>> errors) {
        boolean ok = switch (desc.type()) {
            case STRING  -> value instanceof CharSequence;
            case NUMBER  -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case DATE    -> value instanceof CharSequence; // ISO-8601 string; downstream parses
        };
        if (!ok) {
            errors.add(Map.of("field", "parameters." + desc.name(),
                    "message", "must be " + desc.type().name().toLowerCase()));
        }
    }

    private void checkBounds(ParameterDescriptor desc, Object value, List<Map<String, String>> errors) {
        if (desc.type() != ParameterDescriptor.ParamType.NUMBER || !(value instanceof Number num)) {
            return;
        }
        double d = num.doubleValue();
        if (desc.min() != null && d < desc.min().doubleValue()) {
            errors.add(Map.of("field", "parameters." + desc.name(),
                    "message", "must be ≥ " + desc.min()));
        }
        if (desc.max() != null && d > desc.max().doubleValue()) {
            errors.add(Map.of("field", "parameters." + desc.name(),
                    "message", "must be ≤ " + desc.max()));
        }
    }
}

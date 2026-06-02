package com.memcyco.shortener.strategy;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParameterDescriptor(
        String name,
        ParamType type,
        boolean required,
        Object defaultValue,
        Number min,
        Number max,
        String pattern,
        String description
) {
    public enum ParamType { STRING, NUMBER, BOOLEAN, DATE }

    public static ParameterDescriptor stringParam(String name, boolean required, String defaultValue,
                                                  String pattern, String description) {
        return new ParameterDescriptor(name, ParamType.STRING, required, defaultValue,
                null, null, pattern, description);
    }

    public static ParameterDescriptor numberParam(String name, boolean required, Number defaultValue,
                                                  Number min, Number max, String description) {
        return new ParameterDescriptor(name, ParamType.NUMBER, required, defaultValue,
                min, max, null, description);
    }
}

package com.memcyco.shortener.strategy;

import java.util.List;

public record StrategyDescriptor(
        String name,
        String displayName,
        String description,
        List<ParameterDescriptor> parameterSchema
) {
}

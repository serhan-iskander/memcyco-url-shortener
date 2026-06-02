package com.memcyco.shortener.shortlink.api;

import com.memcyco.shortener.strategy.StrategyDescriptor;
import com.memcyco.shortener.strategy.StrategyRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/strategies")
@Tag(name = "Strategies", description = "Read-only listing of available short-code strategies")
public class StrategiesController {

    private final StrategyRegistry registry;

    public StrategiesController(StrategyRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    @Operation(summary = "List available short-code strategies and their parameter schemas")
    public List<StrategyDescriptor> list() {
        return registry.describe();
    }
}

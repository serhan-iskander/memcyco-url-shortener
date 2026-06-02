package com.memcyco.shortener.strategy;

import com.memcyco.shortener.common.error.DuplicateAliasException;
import com.memcyco.shortener.common.error.ParameterValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class CustomAliasStrategy implements ShortCodeStrategy {

    public static final String NAME = "CUSTOM_ALIAS";
    public static final Pattern ALIAS_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,32}$");

    @Override
    public String name() { return NAME; }

    @Override
    public String displayName() { return "Custom alias"; }

    @Override
    public String description() {
        return "User-supplied alias. 3–32 chars, [a-zA-Z0-9_-]. No collision retry — duplicates 409.";
    }

    @Override
    public List<ParameterDescriptor> parameterSchema() {
        return List.of(
                ParameterDescriptor.stringParam("alias", true, null,
                        ALIAS_PATTERN.pattern(), "Desired short code. Must be globally unique among live links.")
        );
    }

    @Override
    public java.util.Map<String, Object> prepareParams(String topLevelAlias,
                                                       java.util.Map<String, Object> params) {
        java.util.Map<String, Object> merged = ShortCodeStrategy.super.prepareParams(topLevelAlias, params);
        if (topLevelAlias != null) {
            merged.put("alias", topLevelAlias);
        }
        return merged;
    }

    @Override
    public String generate(GenerationContext ctx) {
        String alias = readAlias(ctx.parameters());
        if (!ALIAS_PATTERN.matcher(alias).matches()) {
            throw new ParameterValidationException("Invalid alias format",
                    List.of(Map.of("field", "alias", "message",
                            "Must match " + ALIAS_PATTERN.pattern())));
        }
        if (ctx.collisionTest().test(alias)) {
            throw new DuplicateAliasException(alias);
        }
        return alias;
    }

    private String readAlias(Map<String, Object> params) {
        if (params == null || params.get("alias") == null) {
            throw new ParameterValidationException("alias parameter is required",
                    List.of(Map.of("field", "alias", "message", "must not be blank")));
        }
        return params.get("alias").toString();
    }
}

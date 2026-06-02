package com.memcyco.shortener.strategy;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

@Component
public class RandomBase62Strategy implements ShortCodeStrategy {

    static final String NAME = "RANDOM_BASE62";
    static final int DEFAULT_LENGTH = 7;
    static final int MIN_LENGTH = 4;
    static final int MAX_LENGTH = 16;
    private static final int MAX_RETRIES = 5;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String name() { return NAME; }

    @Override
    public String displayName() { return "Random Base62"; }

    @Override
    public String description() {
        return "Generates a cryptographically-random N-character base62 code. Default length 7.";
    }

    @Override
    public List<ParameterDescriptor> parameterSchema() {
        return List.of(
                ParameterDescriptor.numberParam("length", false, DEFAULT_LENGTH,
                        MIN_LENGTH, MAX_LENGTH, "Number of base62 characters (4–16).")
        );
    }

    @Override
    public String generate(GenerationContext ctx) {
        int length = readLength(ctx.parameters());
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String candidate = randomCode(length);
            if (!ctx.collisionTest().test(candidate)) {
                return candidate;
            }
        }
        // Exhausted: bump length by one and try once more — vanishingly unlikely.
        return randomCode(Math.min(length + 1, MAX_LENGTH));
    }

    private int readLength(Map<String, Object> params) {
        if (params == null) {
            return DEFAULT_LENGTH;
        }
        Object raw = params.get("length");
        if (raw == null) {
            return DEFAULT_LENGTH;
        }
        int v = ((Number) raw).intValue();
        return Math.min(MAX_LENGTH, Math.max(MIN_LENGTH, v));
    }

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
                    .charAt(random.nextInt(Base62.RADIX)));
        }
        return sb.toString();
    }
}

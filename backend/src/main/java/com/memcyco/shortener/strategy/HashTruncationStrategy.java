package com.memcyco.shortener.strategy;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

@Component
public class HashTruncationStrategy implements ShortCodeStrategy {

    static final String NAME = "HASH_TRUNC";
    private static final int BASE_LENGTH = 7;
    private static final int MAX_LENGTH = 12;

    @Override
    public String name() { return NAME; }

    @Override
    public String displayName() { return "Hash truncation (SHA-256)"; }

    @Override
    public String description() {
        return "SHA-256 of the URL (plus optional salt), first N base62 characters. "
                + "On collision the truncation length grows by one.";
    }

    @Override
    public List<ParameterDescriptor> parameterSchema() {
        return List.of(
                ParameterDescriptor.stringParam("salt", false, "", null,
                        "Optional salt mixed into the hash so two identical URLs can have different codes.")
        );
    }

    @Override
    public String generate(GenerationContext ctx) {
        String salt = readSalt(ctx.parameters());
        byte[] digest = sha256((ctx.originalUrl() + ":" + salt).getBytes(StandardCharsets.UTF_8));
        for (int len = BASE_LENGTH; len <= MAX_LENGTH; len++) {
            String candidate = Base62.encodeBytes(digest, len);
            if (!ctx.collisionTest().test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Hash-truncation strategy could not produce a non-colliding code up to length " + MAX_LENGTH);
    }

    private String readSalt(Map<String, Object> params) {
        if (params == null) {
            return "";
        }
        Object raw = params.get("salt");
        return raw == null ? "" : raw.toString();
    }

    private byte[] sha256(byte[] in) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(in);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

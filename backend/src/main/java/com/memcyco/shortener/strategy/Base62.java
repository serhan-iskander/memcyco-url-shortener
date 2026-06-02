package com.memcyco.shortener.strategy;

/** Small zero-dep base62 helper used by sequential / hash strategies. */
public final class Base62 {

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    public static final int RADIX = ALPHABET.length;

    private Base62() {}

    /** Encode an unsigned long. */
    public static String encode(long value) {
        if (value == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        long v = value;
        while (v > 0) {
            sb.append(ALPHABET[(int) (v % RADIX)]);
            v /= RADIX;
        }
        return sb.reverse().toString();
    }

    /** Encode the first {@code chars} base62 characters of the given big-endian byte buffer. */
    public static String encodeBytes(byte[] bytes, int chars) {
        StringBuilder sb = new StringBuilder(chars);
        // Walk the buffer 6 bits at a time (≈ log2 62) to emit base62 chars; simple but
        // not strictly information-preserving — fine because we only need a deterministic
        // string slice for the hash strategy.
        for (int i = 0; i < chars; i++) {
            int b = bytes[i % bytes.length] & 0xFF;
            sb.append(ALPHABET[b % RADIX]);
        }
        return sb.toString();
    }
}

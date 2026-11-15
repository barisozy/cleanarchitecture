package com.barisozy.cleanarchitecture.fixtures;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * The token contract of the template, shared by the tests that issue or verify an access token.
 *
 * <p>The signing key is a fixed 32 byte value. It is a test fixture, not a secret: the production
 * key comes from the environment.
 */
public final class AuthTestData {

    public static final String ISSUER = "https://test.example.com";
    public static final String AUDIENCE = "cleanarchitecture-test";
    public static final Duration TTL = Duration.ofHours(1);

    /** The Base64 encoded 32 byte signing key. */
    public static final String SECRET = Base64.getEncoder().encodeToString(keyBytes());

    private AuthTestData() {}

    /** The raw signing key. */
    public static byte[] keyBytes() {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) (index + 1);
        }
        return key;
    }

    /** A key of fewer than 32 bytes, which the configuration has to reject. */
    public static String tooShortSecret() {
        return Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));
    }
}

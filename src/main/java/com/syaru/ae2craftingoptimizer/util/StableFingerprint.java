package com.syaru.ae2craftingoptimizer.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class StableFingerprint {
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    });

    private StableFingerprint() {
    }

    public static String sha256(CharSequence value) {
        MessageDigest digest = SHA_256.get();
        digest.reset();
        return HexFormat.of().formatHex(digest.digest(value.toString().getBytes(StandardCharsets.UTF_8)));
    }
}

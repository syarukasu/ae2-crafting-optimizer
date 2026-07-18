package com.syaru.ae2craftingoptimizer.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class StableFingerprintTest {
    @Test
    void producesDeterministicSha256Hex() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                StableFingerprint.sha256("abc"));
        assertEquals(64, StableFingerprint.sha256("same input").length());
        assertEquals(StableFingerprint.sha256("same input"), StableFingerprint.sha256("same input"));
        assertNotEquals(StableFingerprint.sha256("pattern-a"), StableFingerprint.sha256("pattern-b"));
    }
}

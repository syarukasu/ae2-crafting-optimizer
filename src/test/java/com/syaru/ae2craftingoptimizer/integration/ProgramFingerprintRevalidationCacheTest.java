package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProgramFingerprintRevalidationCacheTest {
    @Test
    void reusesOnlyFingerprintsProvenInTheCurrentGeneration() {
        var cache = new ProgramFingerprintRevalidationCache();

        assertFalse(cache.contains(10L, 20L, "root-a"));
        cache.record(10L, 20L, "root-a");

        assertTrue(cache.contains(10L, 20L, "root-a"));
        assertFalse(cache.contains(10L, 20L, "root-b"));
    }

    @Test
    void generationChangeInvalidatesEveryPriorProof() {
        var cache = new ProgramFingerprintRevalidationCache();
        cache.record(10L, 20L, "root-a");

        assertFalse(cache.contains(11L, 20L, "root-a"));
        cache.record(11L, 20L, "root-a");
        assertFalse(cache.contains(11L, 21L, "root-a"));
    }
}

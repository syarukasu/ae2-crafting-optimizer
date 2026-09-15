package com.syaru.ae2craftingoptimizer.api.batch.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PatternBatchV2ApiTest {
    @Test
    void adapterPresenceMatchesThePublicRegistrySnapshot() {
        assertEquals(
                !PatternBatchV2Api.registeredAdapterIds().isEmpty(),
                PatternBatchV2Api.hasRegisteredAdapters());
    }
}

package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProviderPatternGenerationTrackerTest {
    @Test
    void coalescesOnlyAe2OwnedProviderImplementations() {
        assertTrue(ProviderPatternGenerationTracker.isRefreshCoalescingSafe(
                "appeng.helpers.patternprovider.PatternProviderLogic"));
        assertFalse(ProviderPatternGenerationTracker.isRefreshCoalescingSafe(
                "example.addon.CustomPatternProviderLogic"));
        assertFalse(ProviderPatternGenerationTracker.isRefreshCoalescingSafe((String) null));
    }
}

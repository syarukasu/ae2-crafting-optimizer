package com.syaru.ae2craftingoptimizer.batch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExactMultisetMatcherTest {
    @Test
    void assignsAmbiguousTagLikeRequirementsWithoutDoubleSpending() {
        var broad = (ExactMultisetMatcher.Requirement<String>) key -> key.startsWith("copper") ? 1L : 0L;
        var strict = (ExactMultisetMatcher.Requirement<String>) key -> key.equals("copper_ingot") ? 1L : 0L;

        assertTrue(ExactMultisetMatcher.matchesExactly(
                Map.of("copper_ingot", 1L, "copper_dust", 1L),
                List.of(broad, strict)));
    }

    @Test
    void rejectsLeftoversAndInsufficientCounts() {
        var oneIron = (ExactMultisetMatcher.Requirement<String>) key -> key.equals("iron") ? 1L : 0L;
        assertFalse(ExactMultisetMatcher.matchesExactly(Map.of("iron", 2L), List.of(oneIron)));
        assertFalse(ExactMultisetMatcher.matchesExactly(Map.of("iron", 1L), List.of(oneIron, oneIron)));
    }
}

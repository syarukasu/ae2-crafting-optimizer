package com.syaru.ae2craftingoptimizer.batch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;
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

    @Test
    void handlesItemFluidAndChemicalKeysWithoutSharingCounts() {
        var item = (ExactMultisetMatcher.Requirement<String>) key -> key.equals("item:ore") ? 2L : 0L;
        var fluid = (ExactMultisetMatcher.Requirement<String>) key -> key.equals("fluid:water") ? 1_000L : 0L;
        var chemical = (ExactMultisetMatcher.Requirement<String>) key -> key.equals("chemical:oxygen") ? 500L : 0L;

        assertTrue(ExactMultisetMatcher.matchesExactly(
                Map.of("item:ore", 2L, "fluid:water", 1_000L, "chemical:oxygen", 500L),
                List.of(item, fluid, chemical)));
    }

    @Test
    void ambiguousImpossibleSearchFailsWithinItsHardBudget() {
        Map<String, Long> available = new LinkedHashMap<>();
        for (int index = 0; index < 13; index++) {
            available.put("candidate-" + index, 1L);
        }
        var broad = (ExactMultisetMatcher.Requirement<String>) key -> key.startsWith("candidate-") ? 1L : 0L;
        List<ExactMultisetMatcher.Requirement<String>> requirements = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            requirements.add(broad);
        }

        assertFalse(ExactMultisetMatcher.matchesExactly(available, requirements));
    }
}

package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class CraftingCalculationDeduplicatorIdentityTest {
    @Test
    void requesterIdentityUsesReferenceInsteadOfUserEquals() {
        EqualRequester first = new EqualRequester();
        EqualRequester second = new EqualRequester();

        var firstKey = new CraftingCalculationDeduplicator.RequesterIdentity(first);
        var sameReference = new CraftingCalculationDeduplicator.RequesterIdentity(first);
        var secondKey = new CraftingCalculationDeduplicator.RequesterIdentity(second);

        assertEquals(firstKey, sameReference);
        assertNotEquals(firstKey, secondKey);
    }

    @Test
    void actionSourceIdentityDoesNotMergeEqualButDistinctContexts() {
        EqualRequester first = new EqualRequester();
        EqualRequester second = new EqualRequester();

        var firstKey = new CraftingCalculationDeduplicator.ActionSourceIdentity(first);
        var sameReference = new CraftingCalculationDeduplicator.ActionSourceIdentity(first);
        var secondKey = new CraftingCalculationDeduplicator.ActionSourceIdentity(second);

        assertEquals(firstKey, sameReference);
        assertNotEquals(firstKey, secondKey);
    }

    private static final class EqualRequester {
        @Override
        public boolean equals(Object other) {
            return other instanceof EqualRequester;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}

package com.syaru.ae2craftingoptimizer.golden;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CanonicalFixtureFuzzTest {
    @Test
    void invalidFixtureShapesFailClosedBeforeEvaluation() {
        assertThrows(
                NullPointerException.class,
                () -> new CanonicalCraftingFixture(
                        "invalid",
                        "output",
                        BigInteger.ONE,
                        List.of(),
                        Map.of("input", null),
                        false));
        assertThrows(
                IllegalArgumentException.class,
                () -> CanonicalCraftingFixture.slot(
                        "empty",
                        BigInteger.ONE));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CanonicalCraftingFixture.InputSlot(
                        "slot",
                        List.of("input"),
                        BigInteger.ZERO));
    }

    @Test
    void boundaryRequestsRemainExactBeyondSignedLong() {
        CanonicalCraftingFixture fixture = CanonicalFixtureCatalog.all().stream()
                .filter(value -> value.id().equals("boundary_long_max_plus_one"))
                .findFirst()
                .orElseThrow();
        CanonicalCraftingResult result = CanonicalCraftingEvaluator.reference(fixture);
        org.junit.jupiter.api.Assertions.assertEquals(
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                result.requestedAmount());
        org.junit.jupiter.api.Assertions.assertEquals(
                fixture.requestedAmount(),
                result.usedItems().get("boundary_input"));
    }
}

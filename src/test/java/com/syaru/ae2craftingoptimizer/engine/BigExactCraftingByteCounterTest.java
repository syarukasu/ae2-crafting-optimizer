package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BigExactCraftingByteCounterTest {
    @Test
    void agreesWithCheckedLongCounterInsideLongRange() {
        CompiledPattern<String> root = pattern("root", "intermediate", 2L, "result");
        CompiledPattern<String> intermediate = pattern("intermediate", "raw", 3L, "intermediate");
        Map<String, CompiledPattern<String>> patterns =
                Map.of("result", root, "intermediate", intermediate);

        long expected = ExactCraftingByteCounter.calculate(
                "result",
                10L,
                patterns,
                Map.of("root", 10L, "intermediate", 10L),
                ignored -> 8L);
        BigInteger actual = BigExactCraftingByteCounter.calculate(
                "result",
                BigInteger.TEN,
                patterns,
                Map.of("root", BigInteger.TEN, "intermediate", BigInteger.TEN),
                ignored -> 8L,
                512);

        assertEquals(BigInteger.valueOf(expected), actual);
    }

    @Test
    void keepsSixtyFourDigitRequestExactWithoutDoubleRounding() {
        BigInteger request = BigInteger.TEN.pow(64).subtract(BigInteger.ONE);

        BigInteger bytes = BigExactCraftingByteCounter.calculate(
                "result",
                request,
                Map.of(),
                Map.of(),
                ignored -> 8L,
                512);

        // Leaf stack = request byte, plus one CraftingTree node (8 bytes).
        assertEquals(request.add(BigInteger.valueOf(8L)), bytes);
    }

    @Test
    void rejectsIntermediateValuePastConfiguredMagnitude() {
        CompiledPattern<String> root = pattern("root", "raw", Long.MAX_VALUE, "result");

        assertThrows(IllegalArgumentException.class, () -> BigExactCraftingByteCounter.calculate(
                "result",
                BigInteger.ONE.shiftLeft(120),
                Map.of("result", root),
                Map.of("root", BigInteger.ONE.shiftLeft(120)),
                ignored -> 8L,
                128));
    }

    @Test
    void keepsTwoDistinctLongMaximumChemicalInputsExact() {
        CompiledPattern<String> root = new CompiledPattern<>(
                "pressurized_reaction",
                List.of(
                        new CompiledPattern.InputSlot<>(List.of(
                                new CompiledPattern.Stack<>("gas_a", Long.MAX_VALUE))),
                        new CompiledPattern.InputSlot<>(List.of(
                                new CompiledPattern.Stack<>("gas_b", Long.MAX_VALUE)))),
                Map.of("result", 1L),
                true);
        Map<String, CompiledPattern<String>> patterns = Map.of("result", root);
        Map<String, BigInteger> executions = Map.of("pressurized_reaction", BigInteger.ONE);

        BigInteger exact = BigExactCraftingByteCounter.calculate(
                "result",
                BigInteger.ONE,
                patterns,
                executions,
                ignored -> 8L,
                256);

        // 二種類は別Keyなので各Long.MAX_VALUEを保持できるが、容量合計はlongを超える。
        assertEquals(
                BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO)
                        .add(BigInteger.valueOf(26L)),
                exact);
        // AE2標準long経路は有限doubleを最後のcastで飽和し、exact経路だけが真の合計を保持する。
        assertEquals(
                Long.MAX_VALUE,
                ExactCraftingByteCounter.calculate(
                        "result",
                        1L,
                        patterns,
                        Map.of("pressurized_reaction", 1L),
                        ignored -> 8L));
    }

    @Test
    void appliesAppliedMekanisticsChemicalByteScaleWithoutPrecisionLoss() {
        CompiledPattern<String> root = new CompiledPattern<>(
                "chemical_process",
                List.of(
                        new CompiledPattern.InputSlot<>(List.of(
                                new CompiledPattern.Stack<>("gas_a", Long.MAX_VALUE))),
                        new CompiledPattern.InputSlot<>(List.of(
                                new CompiledPattern.Stack<>("gas_b", Long.MAX_VALUE)))),
                Map.of("result", 1L),
                true);

        // Applied Mekanistics 1.4.3は化学物質を8000 mB/byteで換算する。
        BigInteger exact = BigExactCraftingByteCounter.calculate(
                "result",
                BigInteger.ONE,
                Map.of("result", root),
                Map.of("chemical_process", BigInteger.ONE),
                key -> key.startsWith("gas_") ? 8_000L : 8L,
                256);

        assertEquals(new BigInteger("18446744073709578"), exact);
    }

    @Test
    void acceptsExactCpuCapacityAfterStackUnitConversion() {
        for (int digits : List.of(1024, BigCountMath.HARD_MAXIMUM_DECIMAL_DIGITS)) {
            BigInteger capacity = BigInteger.TEN.pow(digits).subtract(BigInteger.ONE);
            BigInteger amount = capacity.subtract(BigInteger.valueOf(8));
            assertEquals(capacity, BigExactCraftingByteCounter.calculate(
                    "item", amount, Map.of(), Map.of(), key -> 8L, capacity.bitLength()));
            assertEquals(ceil(capacity.multiply(BigInteger.valueOf(8)), BigInteger.valueOf(8000))
                            .add(BigInteger.valueOf(8)),
                    BigExactCraftingByteCounter.calculate(
                            "chemical", capacity, Map.of(), Map.of(), key -> 8000L, capacity.bitLength()));
        }
    }

    @Test
    void combinesFractionalChargesWithoutBoundingUnreducedNumeratorsAsCpuBytes() {
        for (BigInteger amount : List.of(BigInteger.ONE, BigCountMath.hardMaximumValue().divide(BigInteger.TEN))) {
            var trace = new CraftingPlanTrace<>(List.of(
                    new CraftingPlanTrace.Charge<>("a", amount, 1),
                    new CraftingPlanTrace.Charge<>("b", amount, 1),
                    new CraftingPlanTrace.Charge<String>(null, BigInteger.valueOf(24), 1)));
            BigInteger expected = ceil(amount.multiply(BigInteger.valueOf(64)), BigInteger.valueOf(15))
                    .add(BigInteger.valueOf(24));
            assertEquals(expected, BigExactCraftingByteCounter.calculate(trace,
                    key -> key.equals("a") ? 3L : 5L, BigCountMath.HARD_MAXIMUM_BITS));
        }
    }

    @Test
    void stillRejectsRealCountsOrFinalCpuBytesAboveTheLimit() {
        BigInteger maximum = BigCountMath.hardMaximumValue();
        assertThrows(IllegalArgumentException.class, () -> BigExactCraftingByteCounter.calculate(
                "item", maximum.subtract(BigInteger.valueOf(7)), Map.of(), Map.of(),
                key -> 8L, BigCountMath.HARD_MAXIMUM_BITS));
        assertThrows(IllegalArgumentException.class, () -> BigExactCraftingByteCounter.calculate(
                "fluid", maximum.add(BigInteger.ONE), Map.of(), Map.of(),
                key -> 8000L, BigCountMath.HARD_MAXIMUM_BITS));
    }

    @Test
    void mixedUnitsMatchIndependentRationalSumWithOneFinalRounding() {
        long[] units = {1L, 3L, 5L, 8L, 1000L, 8000L, Long.MAX_VALUE};
        var random = new java.util.Random(190);
        for (int sample = 0; sample < 40; sample++) {
            var charges = new java.util.ArrayList<CraftingPlanTrace.Charge<Integer>>();
            BigInteger numerator = BigInteger.ZERO, denominator = BigInteger.ONE;
            for (int i = 0; i < 50; i++) {
                int key = random.nextInt(units.length);
                BigInteger amount = new BigInteger(140, random);
                charges.add(new CraftingPlanTrace.Charge<>(key, amount, 1));
                BigInteger unit = BigInteger.valueOf(units[key]);
                numerator = numerator.multiply(unit).add(amount.multiply(BigInteger.valueOf(8)).multiply(denominator));
                denominator = denominator.multiply(unit);
                BigInteger gcd = numerator.gcd(denominator);
                numerator = numerator.divide(gcd);
                denominator = denominator.divide(gcd);
            }
            BigInteger overhead = BigInteger.valueOf(24);
            charges.add(new CraftingPlanTrace.Charge<Integer>(null, overhead, 1));
            assertEquals(ceil(numerator, denominator).add(overhead),
                    BigExactCraftingByteCounter.calculate(new CraftingPlanTrace<>(charges),
                            key -> units[key], 512));
        }
    }

    @Test
    void emptyTraceStillValidatesMagnitudeConfiguration() {
        var empty = new CraftingPlanTrace<String>(List.of());
        assertEquals(BigInteger.ZERO, BigExactCraftingByteCounter.calculate(empty, key -> 8L, 512));
        assertThrows(IllegalArgumentException.class,
                () -> BigExactCraftingByteCounter.calculate(empty, key -> 8L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> BigExactCraftingByteCounter.calculate(empty, key -> 8L, BigCountMath.HARD_MAXIMUM_BITS + 1));
    }

    private static BigInteger ceil(BigInteger numerator, BigInteger denominator) {
        var parts = numerator.divideAndRemainder(denominator);
        return parts[0].add(parts[1].signum() == 0 ? BigInteger.ZERO : BigInteger.ONE);
    }

    private static CompiledPattern<String> pattern(
            String id,
            String input,
            long inputAmount,
            String output) {
        return new CompiledPattern<>(
                id,
                List.of(new CompiledPattern.InputSlot<>(
                        List.of(new CompiledPattern.Stack<>(input, inputAmount)))),
                Map.of(output, 1L),
                true);
    }
}

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
        assertThrows(ArithmeticException.class, () -> ExactCraftingByteCounter.calculate(
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

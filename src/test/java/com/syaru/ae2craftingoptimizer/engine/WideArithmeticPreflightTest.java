package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WideArithmeticPreflightTest {
    @Test
    void detectsTwoDistinctMaximumChemicalInputs() {
        CompiledPattern<String> root = pattern(
                "chemical_process",
                List.of(stack("gas_a", Long.MAX_VALUE), stack("gas_b", Long.MAX_VALUE)),
                "result");

        assertTrue(WideArithmeticPreflight.requiresWideArithmetic(
                "result",
                BigInteger.ONE,
                Map.of("result", root),
                key -> key.startsWith("gas_") ? 8_000L : 8L,
                256));
    }

    @Test
    void detectsMaximumChemicalInputsHiddenBehindIntermediatePattern() {
        CompiledPattern<String> root = pattern(
                "root", List.of(stack("intermediate", 1L)), "result");
        CompiledPattern<String> intermediate = pattern(
                "chemical_process",
                List.of(stack("gas_a", Long.MAX_VALUE), stack("gas_b", Long.MAX_VALUE)),
                "intermediate");

        assertTrue(WideArithmeticPreflight.requiresWideArithmetic(
                "result",
                BigInteger.ONE,
                Map.of("result", root, "intermediate", intermediate),
                key -> key.startsWith("gas_") ? 8_000L : 8L,
                256));
    }

    @Test
    void leavesOrdinaryRecipeOnStandardAe2Path() {
        CompiledPattern<String> root = pattern(
                "ordinary",
                List.of(stack("iron", 64L), stack("carbon", 64L)),
                "result");

        assertFalse(WideArithmeticPreflight.requiresWideArithmetic(
                "result",
                BigInteger.valueOf(1_000L),
                Map.of("result", root),
                ignored -> 8L,
                256));
    }

    @Test
    void detectsWideDemandAfterTwoBranchesConvergeOnOneIntermediate() {
        long halfPastSignedLong = (Long.MAX_VALUE / 2L) + 1L;
        CompiledPattern<String> root = pattern(
                "root",
                List.of(stack("branch_a", 1L), stack("branch_b", 1L)),
                "result");
        CompiledPattern<String> branchA = pattern(
                "branch_a",
                List.of(stack("shared", halfPastSignedLong)),
                "branch_a");
        CompiledPattern<String> branchB = pattern(
                "branch_b",
                List.of(stack("shared", halfPastSignedLong)),
                "branch_b");
        CompiledPattern<String> shared = pattern(
                "shared",
                List.of(stack("raw", 1L)),
                "shared");
        CompiledCraftingGraph<String> graph =
                CompiledCraftingGraph.compile(1L, List.of(root, branchA, branchB, shared));
        CompiledRootProgram<String> program = CompiledRootProgram.tryCompile(
                        graph,
                        "result",
                        Set.of()::contains)
                .orElseThrow();

        assertTrue(WideArithmeticPreflight.requiresWideArithmetic(
                "result",
                BigInteger.ONE,
                program,
                ignored -> 8L,
                256));
    }

    private static CompiledPattern<String> pattern(
            String id,
            List<CompiledPattern.Stack<String>> inputs,
            String output) {
        return new CompiledPattern<>(
                id,
                inputs.stream()
                        .map(input -> new CompiledPattern.InputSlot<>(List.of(input)))
                        .toList(),
                Map.of(output, 1L),
                true);
    }

    private static CompiledPattern.Stack<String> stack(String key, long amount) {
        return new CompiledPattern.Stack<>(key, amount);
    }
}

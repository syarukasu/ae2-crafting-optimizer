package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExactCraftingByteCounterTest {
    @Test
    void followsAe2TreeOrderAndNodeOverhead() {
        CompiledPattern<String> root = pattern("root", "intermediate", 2L, "result");
        CompiledPattern<String> intermediate = pattern("intermediate", "raw", 3L, "intermediate");

        long bytes = ExactCraftingByteCounter.calculate(
                "result",
                10L,
                Map.of("result", root, "intermediate", intermediate),
                Map.of("root", 10L, "intermediate", 10L),
                ignored -> 8L);

        // Stack 10+20+30、Pattern 10+10、Tree node 3*8。
        assertEquals(104L, bytes);
    }

    @Test
    void doesNotExpandChildrenWhenInventoryAvoidedThePattern() {
        CompiledPattern<String> root = pattern("root", "intermediate", 2L, "result");
        CompiledPattern<String> intermediate = pattern("intermediate", "raw", 3L, "intermediate");

        long bytes = ExactCraftingByteCounter.calculate(
                "result",
                10L,
                Map.of("result", root, "intermediate", intermediate),
                Map.of("root", 10L),
                ignored -> 8L);

        assertEquals(56L, bytes);
    }

    @Test
    void rejectsOverflowBeforeItCanBecomeNegative() {
        CompiledPattern<String> root = pattern("root", "raw", Long.MAX_VALUE, "result");
        assertThrows(CountOverflowException.class, () -> ExactCraftingByteCounter.calculate(
                "result",
                2L,
                Map.of("result", root),
                Map.of("root", 2L),
                ignored -> 8L));
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

package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Ae2BigCraftingPlanFactoryFingerprintTest {
    @Test
    void outputAmountChangeInvalidatesTheProgramFingerprint() {
        String output = "output";
        String input = "input";
        var onePerCraft = compile(output, input, 1L);
        var twoPerCraft = compile(output, input, 2L);

        assertNotEquals(
                Ae2BigCraftingPlanFactory.computeProgramFingerprint(
                        onePerCraft, key -> key),
                Ae2BigCraftingPlanFactory.computeProgramFingerprint(
                        twoPerCraft, key -> key));
    }

    private static CompiledRootProgram<String> compile(
            String output,
            String input,
            long outputAmount) {
        var slot = new CompiledPattern.InputSlot<>(
                List.of(new CompiledPattern.Stack<>(input, 1L)));
        var pattern = new CompiledPattern<>(
                "aco:fingerprint_output_amount",
                List.of(slot),
                Map.of(output, outputAmount),
                false);
        return CompiledRootProgram.tryCompile(
                        CompiledCraftingGraph.compile(
                                1L, List.of(pattern)),
                        output,
                        Set.of()::contains)
                .orElseThrow();
    }
}

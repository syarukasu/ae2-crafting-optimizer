package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Ae2BigCraftingPlanFactoryRootWindowTest {
    /** 8^21 = 2^63。signed long境界を一段だけ超える再現条件である。 */
    private static final int OVERFLOWING_COMPRESSION_STAGES = 21;
    /** 試験値2^63と中間演算を十分収める小さなBigInteger上限。 */
    private static final int TEST_MAXIMUM_BITS = 128;
    /** 実Config既定と同じroot実行Window上限。 */
    private static final long DEFAULT_ROOT_WINDOW = 65_536L;

    @Test
    void keepsAnExactPlanWhenOneRootNeedsMoreThanLongMaximumInputs() {
        CompiledRootProgram<String> program = compressionProgram(
                OVERFLOWING_COMPRESSION_STAGES);
        BigInteger requiredRaw = BigInteger.ONE.shiftLeft(63);
        var inventory = program.captureBigInventory(
                key -> key.equals("stage_0") ? requiredRaw : BigInteger.ZERO,
                TEST_MAXIMUM_BITS);

        BigCraftingPlan<String> exactPlan = program.planBig(
                BigInteger.ONE,
                inventory,
                PlanningGuard.none(),
                TEST_MAXIMUM_BITS);
        var decision = Ae2BigCraftingPlanFactory.rootWindowDecision(
                program,
                BigInteger.ONE,
                DEFAULT_ROOT_WINDOW,
                TEST_MAXIMUM_BITS);

        assertTrue(exactPlan.craftable());
        assertEquals(requiredRaw, exactPlan.usedInventory().get("stage_0"));
        assertEquals(
                Ae2BigCraftingPlanFactory.ExecutionMode.EXACT_PATTERN_EXECUTOR,
                decision.mode());
        assertEquals(0L, decision.maximumRootExecutions());
    }

    @Test
    void keepsTheSameBoundaryExactForAMissingSimulation() {
        CompiledRootProgram<String> program = compressionProgram(
                OVERFLOWING_COMPRESSION_STAGES);
        var emptyInventory = program.captureBigInventory(
                ignored -> BigInteger.ZERO,
                TEST_MAXIMUM_BITS);

        BigCraftingPlan<String> simulation = program.planBig(
                BigInteger.ONE,
                emptyInventory,
                PlanningGuard.none(),
                TEST_MAXIMUM_BITS);

        assertEquals(
                BigInteger.ONE.shiftLeft(63),
                simulation.missing().get("stage_0"));
    }

    @Test
    void retainsRootWindowsImmediatelyBelowTheBoundary() {
        CompiledRootProgram<String> program = compressionProgram(
                OVERFLOWING_COMPRESSION_STAGES - 1);

        var decision = Ae2BigCraftingPlanFactory.rootWindowDecision(
                program,
                BigInteger.ONE,
                DEFAULT_ROOT_WINDOW,
                TEST_MAXIMUM_BITS);

        assertEquals(
                Ae2BigCraftingPlanFactory.ExecutionMode.ROOT_WINDOWS,
                decision.mode());
        assertEquals(1L, decision.maximumRootExecutions());
    }

    private static CompiledRootProgram<String> compressionProgram(int stages) {
        List<CompiledPattern<String>> patterns = new ArrayList<>(stages);
        // 各段は下位素材8個を上位素材1個へ圧縮する、単一路線の確定Patternである。
        for (int stage = 1; stage <= stages; stage++) {
            String input = "stage_" + (stage - 1);
            String output = "stage_" + stage;
            patterns.add(new CompiledPattern<>(
                    "pattern_" + stage,
                    List.of(new CompiledPattern.InputSlot<>(
                            List.of(new CompiledPattern.Stack<>(input, 8L)))),
                    Map.of(output, 1L),
                    false));
        }
        return CompiledRootProgram.tryCompile(
                        CompiledCraftingGraph.compile(1L, patterns),
                        "stage_" + stages,
                        ignored -> false)
                .orElseThrow();
    }
}

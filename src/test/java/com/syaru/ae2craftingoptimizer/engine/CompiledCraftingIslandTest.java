package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompiledCraftingIslandTest {
    private static final int TEST_MAXIMUM_BITS = 512;

    @Test
    void collapsesAThreeStageTreeIntoBoundaryDifferences() {
        var bottom = task("bottom", "raw", 1, "part_1", 1, 900);
        var middle = task("middle", "part_1", 3, "part_2", 1, 300);
        var top = task("top", "part_2", 3, "final", 1, 100);

        var compiled = CompiledCraftingIsland.tryCompile(
                List.of(top, middle, bottom),
                TEST_MAXIMUM_BITS);

        assertTrue(compiled.isPresent());
        assertEquals(1, compiled.orElseThrow().size());
        var island = compiled.orElseThrow().get(0);
        assertEquals(BigInteger.valueOf(900), island.boundaryInputs().get("raw"));
        assertEquals(BigInteger.valueOf(100), island.boundaryOutputs().get("final"));
        assertEquals(BigInteger.valueOf(900), island.internalOutputs().get("part_1"));
        assertEquals(BigInteger.valueOf(300), island.internalOutputs().get("part_2"));
        assertEquals(BigInteger.valueOf(100), island.sinkExecutions());
        assertEquals(BigInteger.valueOf(1300), island.logicalExecutions());
        assertEquals(3, island.criticalPathStages());
        assertTrue(island.fitsSignedLongRuntime());
    }

    @Test
    void preservesExistingIntermediateStockAsAnExternalBoundaryInput() {
        var producer = task("producer", "raw", 1, "middle", 1, 50);
        var consumer = task("consumer", "middle", 1, "final", 1, 100);

        var island = CompiledCraftingIsland.tryCompile(
                        List.of(consumer, producer),
                        TEST_MAXIMUM_BITS)
                .orElseThrow()
                .get(0);

        assertEquals(BigInteger.valueOf(50), island.boundaryInputs().get("raw"));
        assertEquals(BigInteger.valueOf(50), island.boundaryInputs().get("middle"));
        assertEquals(BigInteger.valueOf(100), island.boundaryOutputs().get("final"));
        assertEquals(BigInteger.valueOf(50), island.internalOutputs().get("middle"));
    }

    @Test
    void separatesIndependentCraftingChainsAtExternalMachineBoundaries() {
        var leftBottom = task("left_bottom", "left_raw", 1, "left_mid", 1, 9);
        var leftTop = task("left_top", "left_mid", 3, "left_out", 1, 3);
        var rightBottom = task("right_bottom", "right_raw", 1, "right_mid", 1, 8);
        var rightTop = task("right_top", "right_mid", 2, "right_out", 1, 4);

        var islands = CompiledCraftingIsland.tryCompile(
                        List.of(leftTop, leftBottom, rightTop, rightBottom),
                        TEST_MAXIMUM_BITS)
                .orElseThrow();

        assertEquals(2, islands.size());
        assertTrue(islands.stream()
                .anyMatch(island -> island.boundaryOutputs().containsKey("left_out")));
        assertTrue(islands.stream()
                .anyMatch(island -> island.boundaryOutputs().containsKey("right_out")));
    }

    @Test
    void rejectsCyclesWithoutProducingAPartialIsland() {
        var first = task("first", "second", 1, "first", 1, 1);
        var second = task("second", "first", 1, "second", 1, 1);

        assertTrue(CompiledCraftingIsland.tryCompile(
                        List.of(first, second),
                        TEST_MAXIMUM_BITS)
                .isEmpty());
    }

    @Test
    void rejectsMultipleProducersForTheSameOutput() {
        var first = task("first", "raw_a", 1, "shared", 1, 1);
        var second = task("second", "raw_b", 1, "shared", 1, 1);

        assertTrue(CompiledCraftingIsland.tryCompile(
                        List.of(first, second),
                        TEST_MAXIMUM_BITS)
                .isEmpty());
    }

    @Test
    void keepsBigIntegerMathButRefusesAnUnsafeLongRuntimeProjection() {
        BigInteger executions = BigInteger.valueOf(Long.MAX_VALUE);
        var producer = new CompiledCraftingIsland.Task<>(
                "producer",
                "producer",
                "middle",
                1,
                List.of(new CompiledCraftingIsland.Input<>("raw", 2)),
                executions);
        var consumer = new CompiledCraftingIsland.Task<>(
                "consumer",
                "consumer",
                "final",
                1,
                List.of(new CompiledCraftingIsland.Input<>("middle", 1)),
                executions);

        var island = CompiledCraftingIsland.tryCompile(
                        List.of(consumer, producer),
                        TEST_MAXIMUM_BITS)
                .orElseThrow()
                .get(0);

        assertEquals(executions.multiply(BigInteger.TWO), island.boundaryInputs().get("raw"));
        assertFalse(island.fitsSignedLongRuntime());
    }

    @Test
    void aggregatesNineLongMaximumSlotsWithoutNarrowingTheBoundary() {
        BigInteger executions = BigInteger.valueOf(Long.MAX_VALUE);
        List<CompiledCraftingIsland.Input<String>> inputs =
                new ArrayList<>(9);
        // 作業台九枠が同一素材でも、各枠をlongへ戻さず一つのBigInteger境界へ合算する。
        for (int slot = 0; slot < 9; slot++) {
            inputs.add(new CompiledCraftingIsland.Input<>("raw", 1L));
        }
        var task = new CompiledCraftingIsland.Task<>(
                "nine_slot",
                "nine_slot",
                "final",
                1L,
                inputs,
                executions);

        var island = CompiledCraftingIsland
                .tryCompileIncludingSingletons(
                        List.of(task),
                        TEST_MAXIMUM_BITS)
                .orElseThrow()
                .get(0);

        assertEquals(
                executions.multiply(BigInteger.valueOf(9L)),
                island.boundaryInputs().get("raw"));
        assertEquals(executions, island.boundaryOutputs().get("final"));
        assertEquals(executions, island.sinkExecutions());
        assertFalse(island.fitsSignedLongRuntime());
    }

    @Test
    void compilesASingleDeterministicPatternOnTheNormalEntryPoint() {
        var compiled = CompiledCraftingIsland.tryCompile(
                List.of(task("single", "raw", 1, "final", 1, 1)),
                TEST_MAXIMUM_BITS);

        assertTrue(compiled.isPresent());
        assertEquals(1, compiled.orElseThrow().size());
        assertEquals(
                BigInteger.ONE,
                compiled.orElseThrow().get(0).boundaryInputs().get("raw"));
    }

    @Test
    void acceptsDistinctBoundaryKeysAtTheExactLongMaximum() {
        var producer = new CompiledCraftingIsland.Task<>(
                "producer",
                "producer",
                "middle",
                1,
                List.of(new CompiledCraftingIsland.Input<>("raw_a", Long.MAX_VALUE)),
                BigInteger.ONE);
        var consumer = new CompiledCraftingIsland.Task<>(
                "consumer",
                "consumer",
                "final",
                Long.MAX_VALUE,
                List.of(
                        new CompiledCraftingIsland.Input<>("middle", 1),
                        new CompiledCraftingIsland.Input<>("raw_b", Long.MAX_VALUE)),
                BigInteger.ONE);

        var island = CompiledCraftingIsland.tryCompile(
                        List.of(consumer, producer),
                        TEST_MAXIMUM_BITS)
                .orElseThrow()
                .get(0);

        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), island.boundaryInputs().get("raw_a"));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), island.boundaryInputs().get("raw_b"));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), island.boundaryOutputs().get("final"));
        assertTrue(island.fitsSignedLongRuntime());
    }

    @Test
    void collapsesTheTwentyStageNineWayPackProbeIntoOneWave() {
        int stageCount = 20;
        BigInteger nine = BigInteger.valueOf(9L);
        List<CompiledCraftingIsland.Task<String, String>> tasks =
                new ArrayList<>(stageCount);
        // 第一段だけSeed A五個とSeed B四個を使い、各キーはlong内、合計だけlong超過にする。
        BigInteger firstStageExecutions = nine.pow(stageCount - 1);
        tasks.add(new CompiledCraftingIsland.Task<>(
                "stage_01",
                "stage_01",
                "stage_01",
                1L,
                List.of(
                        new CompiledCraftingIsland.Input<>("seed_a", 5L),
                        new CompiledCraftingIsland.Input<>("seed_b", 4L)),
                firstStageExecutions));
        // 第二段以降は直前素材九個を一個へ圧縮し、第二十段の実行回数を一回にする。
        for (int stage = 2; stage <= stageCount; stage++) {
            int remainingStages = stageCount - stage;
            String output = stageName(stage);
            String input = stageName(stage - 1);
            tasks.add(new CompiledCraftingIsland.Task<>(
                    output,
                    output,
                    output,
                    1L,
                    List.of(new CompiledCraftingIsland.Input<>(
                            input,
                            9L)),
                    nine.pow(remainingStages)));
        }

        var island = CompiledCraftingIsland.tryCompile(
                        tasks,
                        TEST_MAXIMUM_BITS)
                .orElseThrow()
                .get(0);

        assertEquals(stageCount, island.tasks().size());
        assertEquals(firstStageExecutions.multiply(BigInteger.valueOf(5L)),
                island.boundaryInputs().get("seed_a"));
        assertEquals(firstStageExecutions.multiply(BigInteger.valueOf(4L)),
                island.boundaryInputs().get("seed_b"));
        assertEquals(BigInteger.ONE, island.boundaryOutputs().get("stage_20"));
        assertEquals(BigInteger.ONE, island.sinkExecutions());
        assertEquals(stageCount, island.criticalPathStages());
        assertTrue(island.fitsSignedLongRuntime());
    }

    @Test
    void keepsTwentyStagesForSmallLongAndHugeExactOrders() {
        int stageCount = 20;
        int maximumBits = 4096;
        List<BigInteger> quantities = List.of(
                BigInteger.ONE,
                BigInteger.valueOf(1_000L),
                BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.TEN.pow(1_024).subtract(BigInteger.ONE));

        // 注文数量ごとに同じ20ノードを構築し、数量が段数やTask件数へ漏れないことを固定する。
        for (BigInteger quantity : quantities) {
            List<CompiledCraftingIsland.Task<String, String>> tasks =
                    new ArrayList<>(stageCount);
            for (int stage = 1; stage <= stageCount; stage++) {
                String input = stage == 1
                        ? "raw"
                        : stageName(stage - 1);
                String output = stageName(stage);
                tasks.add(new CompiledCraftingIsland.Task<>(
                        output,
                        output,
                        output,
                        1L,
                        List.of(new CompiledCraftingIsland.Input<>(
                                input,
                                1L)),
                        quantity));
            }

            var island = CompiledCraftingIsland
                    .tryCompileIncludingSingletons(tasks, maximumBits)
                    .orElseThrow()
                    .get(0);

            assertEquals(stageCount, island.tasks().size());
            assertEquals(stageCount, island.criticalPathStages());
            assertEquals(quantity, island.boundaryInputs().get("raw"));
            assertEquals(
                    quantity,
                    island.boundaryOutputs().get("stage_20"));
        }
    }

    @Test
    void keepsTheExplicitSingletonEntryPointCompatible() {
        var task = task("single", "raw", 2, "final", 1, 3);

        var islands = CompiledCraftingIsland
                .tryCompileIncludingSingletons(
                        List.of(task),
                        TEST_MAXIMUM_BITS)
                .orElseThrow();

        assertEquals(1, islands.size());
        assertEquals(1, islands.get(0).criticalPathStages());
        assertEquals(
                BigInteger.valueOf(6L),
                islands.get(0).boundaryInputs().get("raw"));
    }

    @Test
    void rejectsAnIslandAboveTheConfiguredBigIntegerBitLimit() {
        var producer = task("producer", "raw", 3, "middle", 1, Long.MAX_VALUE);
        var consumer = task("consumer", "middle", 1, "final", 1, Long.MAX_VALUE);

        assertTrue(CompiledCraftingIsland.tryCompile(
                        List.of(consumer, producer),
                        Long.SIZE)
                .isEmpty());
    }

    @Test
    void validatesADeepChainWithoutRecursiveStackUse() {
        // 再帰DFSなら実用JVM stackを超え得る長さを使い、反復循環判定を固定する。
        int chainLength = 10_000;
        List<CompiledCraftingIsland.Task<String, String>> tasks =
                new ArrayList<>(chainLength);
        for (int index = 0; index < chainLength; index++) {
            String input = index == 0 ? "raw" : "node_" + (index - 1);
            String output = "node_" + index;
            tasks.add(task("task_" + index, input, 1, output, 1, 1));
        }

        var islands = CompiledCraftingIsland.tryCompile(
                        tasks,
                        TEST_MAXIMUM_BITS)
                .orElseThrow();

        assertEquals(1, islands.size());
        assertEquals(chainLength, islands.get(0).tasks().size());
    }

    private static CompiledCraftingIsland.Task<String, String> task(
            String id,
            String input,
            long inputAmount,
            String output,
            long outputAmount,
            long executions) {
        return new CompiledCraftingIsland.Task<>(
                id,
                id,
                output,
                outputAmount,
                List.of(new CompiledCraftingIsland.Input<>(input, inputAmount)),
                BigInteger.valueOf(executions));
    }

    private static String stageName(int stage) {
        // 実ゲームのProbe IDと同じ二桁表記に揃え、段間キーの誤接続を検出する。
        return "stage_" + (stage < 10 ? "0" : "") + stage;
    }
}

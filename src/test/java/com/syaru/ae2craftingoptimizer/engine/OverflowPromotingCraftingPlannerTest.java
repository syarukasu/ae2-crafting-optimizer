package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OverflowPromotingCraftingPlannerTest {
    /** 各Creative Chemical TankのGas量がすべてlongに収まる最後の注文数。 */
    private static final long INDIVIDUAL_GAS_SAFE_REQUEST = 9L;

    /** 各Creative Chemical TankのGas量がすべてlongを超える最初の注文数。 */
    private static final long INDIVIDUAL_GAS_OVERFLOW_REQUEST = 10L;

    /** 境界・混成・上位の三層が一回で要求する10B相当のmB量。 */
    private static final long PROBE_COMPRESSION_MULTIPLIER = 10_000L;

    /** 最終混成と結晶化が一回で要求する1B相当のmB量。 */
    private static final long PROBE_FINAL_STAGE_MULTIPLIER = 1_000L;

    /** 各増幅段と最終プローブが一回で生成する個数。 */
    private static final long PROBE_OUTPUT_AMOUNT = 1L;

    @Test
    void retainsLongFastPathWhenAllArithmeticFits() {
        var result = new OverflowPromotingCraftingPlanner<String>().plan(
                graph(2L), "output", BigInteger.valueOf(10L), Map.of());

        assertFalse(result.usesBigInteger());
        assertTrue(result.provenEquivalent());
        assertTrue(result instanceof OverflowPromotingCraftingPlanner.LongResult<?>);
        OverflowPromotingCraftingPlanner.LongResult<?> longResult =
                (OverflowPromotingCraftingPlanner.LongResult<?>) result;
        var plan = longResult.plan();
        assertEquals(10L, plan.patternExecutions().get("output"));
    }

    @Test
    void recomputesFromSnapshotAfterIntermediateMultiplicationOverflows() {
        BigInteger request = BigInteger.valueOf(Long.MAX_VALUE);
        var result = new OverflowPromotingCraftingPlanner<String>().plan(
                graph(2L), "output", request, Map.of());

        assertTrue(result.usesBigInteger());
        assertTrue(result.provenEquivalent());
        var plan = assertInstanceOf(OverflowPromotingCraftingPlanner.BigResult.class, result).plan();
        assertEquals(request.multiply(BigInteger.TWO), plan.patternExecutions().get("input"));
    }

    @Test
    void keepsSixteenGasBoundaryProbeOnLongPathBeforeIndividualOverflow() {
        var result = new OverflowPromotingCraftingPlanner<String>(256).plan(
                longBoundaryProbeGraph(),
                "probe",
                BigInteger.valueOf(INDIVIDUAL_GAS_SAFE_REQUEST),
                Map.of());

        assertFalse(result.usesBigInteger());
        assertTrue(result.provenEquivalent());
        assertTrue(result instanceof OverflowPromotingCraftingPlanner.LongResult<?>);
        OverflowPromotingCraftingPlanner.LongResult<?> longResult =
                (OverflowPromotingCraftingPlanner.LongResult<?>) result;
        var plan = longResult.plan();
        assertTrue(plan.missing().values().stream().allMatch(amount -> amount > 0L));
    }

    @Test
    void oneProbeExceedsLongOnlyAfterSixteenGasAmountsAreAggregated() {
        var result = new OverflowPromotingCraftingPlanner<String>(256).plan(
                longBoundaryProbeGraph(), "probe", BigInteger.ONE, Map.of());

        assertTrue(result instanceof OverflowPromotingCraftingPlanner.LongResult<?>);
        OverflowPromotingCraftingPlanner.LongResult<?> longResult =
                (OverflowPromotingCraftingPlanner.LongResult<?>) result;
        var plan = longResult.plan();
        BigInteger expectedIndividualGas = expectedIndividualGas(BigInteger.ONE);
        BigInteger aggregateGas = plan.missing().values().stream()
                .map(BigInteger::valueOf)
                .reduce(BigInteger.ZERO, BigInteger::add);

        assertEquals(BigInteger.TEN.pow(18), expectedIndividualGas);
        assertEquals(expectedIndividualGas.longValueExact(), plan.missing().get("hydrogen"));
        assertTrue(expectedIndividualGas.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) < 0);
        assertTrue(aggregateGas.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
    }

    @Test
    void promotesSixteenGasBoundaryProbePastIndividualLongBoundary() {
        BigInteger request = BigInteger.valueOf(INDIVIDUAL_GAS_OVERFLOW_REQUEST);
        var result = new OverflowPromotingCraftingPlanner<String>(256).plan(
                longBoundaryProbeGraph(), "probe", request, Map.of());

        var plan = assertInstanceOf(OverflowPromotingCraftingPlanner.BigResult.class, result).plan();
        BigInteger expectedIndividualGas = expectedIndividualGas(request);

        assertEquals(expectedIndividualGas, plan.missing().get("hydrogen"));
        assertEquals(expectedIndividualGas, plan.missing().get("oxygen"));
        assertEquals(expectedIndividualGas, plan.missing().get("chlorine"));
        assertEquals(expectedIndividualGas, plan.missing().get("sulfur_dioxide"));
        assertEquals(expectedIndividualGas, plan.missing().get("sulfur_trioxide"));
        assertEquals(expectedIndividualGas, plan.missing().get("hydrogen_chloride"));
        assertEquals(expectedIndividualGas, plan.missing().get("ethene"));
        assertEquals(expectedIndividualGas, plan.missing().get("water_vapor"));
        assertEquals(expectedIndividualGas, plan.missing().get("brine"));
        assertEquals(expectedIndividualGas, plan.missing().get("lithium"));
        assertEquals(expectedIndividualGas, plan.missing().get("sodium"));
        assertEquals(expectedIndividualGas, plan.missing().get("superheated_sodium"));
        assertEquals(expectedIndividualGas, plan.missing().get("steam"));
        assertEquals(expectedIndividualGas, plan.missing().get("osmium"));
        assertEquals(expectedIndividualGas, plan.missing().get("hydrofluoric_acid"));
        assertEquals(expectedIndividualGas, plan.missing().get("sulfuric_acid"));
        assertTrue(expectedIndividualGas.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
    }

    @Test
    void startsOnBigPathWhenRequestAlreadyExceedsLong() {
        BigInteger request = BigInteger.ONE.shiftLeft(256);
        var result = new OverflowPromotingCraftingPlanner<String>().plan(
                graph(1L), "output", request, Map.of());

        assertTrue(result.usesBigInteger());
        assertTrue(result.craftable());
        assertTrue(result.provenEquivalent());
    }

    @Test
    void rejectsIntermediateBigIntegerGrowthPastConfiguredBits() {
        BigInteger request = BigInteger.ONE.shiftLeft(63);
        OverflowPromotingCraftingPlanner<String> planner =
                new OverflowPromotingCraftingPlanner<>(64);

        assertThrows(
                IllegalArgumentException.class,
                () -> planner.plan(graph(2L), "output", request, Map.of()));
    }

    @Test
    void marksAmbiguousPatternSelectionAsShadowOnly() {
        CompiledPattern<String> first = new CompiledPattern<>(
                "first", List.of(), Map.of("output", 1L), true);
        CompiledPattern<String> second = new CompiledPattern<>(
                "second", List.of(), Map.of("output", 1L), true);
        var result = new OverflowPromotingCraftingPlanner<String>().plan(
                CompiledCraftingGraph.compile(1L, List.of(first, second)),
                "output",
                BigInteger.ONE,
                Map.of());

        assertFalse(result.provenEquivalent());
    }

    @Test
    void deterministicProgramDoesNotInspectUnreferencedInventoryEntries() {
        BigInteger outsideConfiguredLimit = BigInteger.ONE.shiftLeft(300);
        var result = new OverflowPromotingCraftingPlanner<String>(256).plan(
                graph(1L),
                "output",
                BigInteger.ONE,
                Map.of("unrelated", outsideConfiguredLimit));

        assertTrue(result.provenEquivalent());
        assertTrue(result.craftable());
    }

    @Test
    void exactBigInventoryCanSatisfyDemandPastLongBoundary() {
        CompiledRootProgram<String> program = CompiledRootProgram.tryCompile(
                        graph(2L),
                        "output",
                        ignored -> false)
                .orElseThrow();
        BigInteger request = BigInteger.valueOf(Long.MAX_VALUE);
        BigInteger exactInput = request.multiply(BigInteger.TWO);
        CompiledRootProgram.BigInventorySnapshot<String> inventory =
                program.captureBigInventory(
                        key -> key.equals("input") ? exactInput : BigInteger.ZERO,
                        256);

        var result = new OverflowPromotingCraftingPlanner<String>(256).plan(
                program,
                request,
                inventory,
                PlanningGuard.none());

        var plan = assertInstanceOf(
                OverflowPromotingCraftingPlanner.BigResult.class,
                result).plan();
        assertTrue(plan.craftable());
        assertEquals(exactInput, plan.usedInventory().get("input"));
        assertFalse(plan.patternExecutions().containsKey("input"));
    }

    private static CompiledCraftingGraph<String> graph(long inputAmount) {
        CompiledPattern<String> output = new CompiledPattern<>(
                "output",
                List.of(new CompiledPattern.InputSlot<>(
                        List.of(new CompiledPattern.Stack<>("input", inputAmount)))),
                Map.of("output", 1L),
                true);
        CompiledPattern<String> input = new CompiledPattern<>(
                "input", List.of(), Map.of("input", 1L), true);
        return CompiledCraftingGraph.compile(1L, List.of(output, input));
    }

    private static CompiledCraftingGraph<String> longBoundaryProbeGraph() {
        CompiledPattern<String> probe = pattern(
                "probe",
                "probe",
                stack("mixture", PROBE_FINAL_STAGE_MULTIPLIER));
        CompiledPattern<String> mixture = new CompiledPattern<>(
                "mixture",
                List.of(
                        new CompiledPattern.InputSlot<>(List.of(stack(
                                "upper_1", PROBE_FINAL_STAGE_MULTIPLIER))),
                        new CompiledPattern.InputSlot<>(List.of(stack(
                                "upper_2", PROBE_FINAL_STAGE_MULTIPLIER)))),
                Map.of("mixture", PROBE_OUTPUT_AMOUNT),
                true);
        CompiledPattern<String> upperOne = pattern(
                "upper_1",
                "upper_1",
                stack("mixed_1", PROBE_COMPRESSION_MULTIPLIER),
                stack("mixed_2", PROBE_COMPRESSION_MULTIPLIER));
        CompiledPattern<String> upperTwo = pattern(
                "upper_2",
                "upper_2",
                stack("mixed_3", PROBE_COMPRESSION_MULTIPLIER),
                stack("mixed_4", PROBE_COMPRESSION_MULTIPLIER));
        CompiledPattern<String> mixedOne = pattern(
                "mixed_1",
                "mixed_1",
                stack("boundary_1", PROBE_COMPRESSION_MULTIPLIER),
                stack("boundary_2", PROBE_COMPRESSION_MULTIPLIER));
        CompiledPattern<String> mixedTwo = pattern(
                "mixed_2",
                "mixed_2",
                stack("boundary_3", PROBE_COMPRESSION_MULTIPLIER),
                stack("boundary_4", PROBE_COMPRESSION_MULTIPLIER));
        CompiledPattern<String> mixedThree = pattern(
                "mixed_3",
                "mixed_3",
                stack("boundary_5", PROBE_COMPRESSION_MULTIPLIER),
                stack("boundary_6", PROBE_COMPRESSION_MULTIPLIER));
        CompiledPattern<String> mixedFour = pattern(
                "mixed_4",
                "mixed_4",
                stack("boundary_7", PROBE_COMPRESSION_MULTIPLIER),
                stack("boundary_8", PROBE_COMPRESSION_MULTIPLIER));
        CompiledPattern<String> boundaryOne = pattern(
                "boundary_1",
                "boundary_1",
                stack("hydrogen", PROBE_COMPRESSION_MULTIPLIER),
                stack("oxygen", PROBE_COMPRESSION_MULTIPLIER));
        CompiledPattern<String> boundaryTwo = pattern(
                "boundary_2",
                "boundary_2",
                stack("chlorine", PROBE_COMPRESSION_MULTIPLIER),
                stack("sulfur_dioxide", PROBE_COMPRESSION_MULTIPLIER));
        CompiledPattern<String> boundaryThree = pattern(
                "boundary_3",
                "boundary_3",
                stack("sulfur_trioxide", PROBE_COMPRESSION_MULTIPLIER),
                stack("hydrogen_chloride", PROBE_COMPRESSION_MULTIPLIER));
        CompiledPattern<String> boundaryFour = pattern(
                "boundary_4",
                "boundary_4",
                stack("ethene", PROBE_COMPRESSION_MULTIPLIER),
                stack("water_vapor", PROBE_COMPRESSION_MULTIPLIER));
        CompiledPattern<String> boundaryFive = pattern(
                "boundary_5",
                "boundary_5",
                stack("brine", PROBE_COMPRESSION_MULTIPLIER),
                stack("lithium", PROBE_COMPRESSION_MULTIPLIER));
        CompiledPattern<String> boundarySix = pattern(
                "boundary_6",
                "boundary_6",
                stack("sodium", PROBE_COMPRESSION_MULTIPLIER),
                stack("superheated_sodium", PROBE_COMPRESSION_MULTIPLIER));
        CompiledPattern<String> boundarySeven = pattern(
                "boundary_7",
                "boundary_7",
                stack("steam", PROBE_COMPRESSION_MULTIPLIER),
                stack("osmium", PROBE_COMPRESSION_MULTIPLIER));
        CompiledPattern<String> boundaryEight = pattern(
                "boundary_8",
                "boundary_8",
                stack("hydrofluoric_acid", PROBE_COMPRESSION_MULTIPLIER),
                stack("sulfuric_acid", PROBE_COMPRESSION_MULTIPLIER));
        return CompiledCraftingGraph.compile(
                PROBE_OUTPUT_AMOUNT,
                List.of(
                        probe,
                        mixture,
                        upperOne,
                        upperTwo,
                        mixedOne,
                        mixedTwo,
                        mixedThree,
                        mixedFour,
                        boundaryOne,
                        boundaryTwo,
                        boundaryThree,
                        boundaryFour,
                        boundaryFive,
                        boundarySix,
                        boundarySeven,
                        boundaryEight));
    }

    private static BigInteger expectedIndividualGas(BigInteger request) {
        return request
                .multiply(BigInteger.valueOf(PROBE_COMPRESSION_MULTIPLIER).pow(3))
                .multiply(BigInteger.valueOf(PROBE_FINAL_STAGE_MULTIPLIER).pow(2));
    }

    @SafeVarargs
    private static CompiledPattern<String> pattern(
            String id,
            String output,
            CompiledPattern.Stack<String>... inputs) {
        List<CompiledPattern.InputSlot<String>> slots = java.util.Arrays.stream(inputs)
                .map(input -> new CompiledPattern.InputSlot<>(List.of(input)))
                .toList();
        return new CompiledPattern<>(
                id,
                slots,
                Map.of(output, PROBE_OUTPUT_AMOUNT),
                true);
    }

    private static CompiledPattern.Stack<String> stack(String key, long amount) {
        return new CompiledPattern.Stack<>(key, amount);
    }
}

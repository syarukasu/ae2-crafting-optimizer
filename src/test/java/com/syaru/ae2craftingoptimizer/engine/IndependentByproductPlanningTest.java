package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IndependentByproductPlanningTest {
    private static final int BITS = BigCountMath.HARD_MAXIMUM_BITS;

    @Test
    void plansProcessingByproductsWithoutReservingOrDiscardingThem() {
        var pattern = process("wash", List.of(slot("ore", 2), slot("water", 1000)),
                Map.of("clean", 3L, "gas", 250L, "sludge", 500L));
        var program = compile(List.of(pattern), "clean");
        var plan = program.planLong(10, program.captureLongInventory(key -> switch (key) {
            case "clean" -> 2;
            case "ore" -> 5;
            case "water" -> 3000;
            default -> Long.MAX_VALUE;
        }), PlanningGuard.none());
        assertEquals(Map.of("wash", 3L), plan.patternExecutions());
        assertEquals(Map.of("clean", 2L, "ore", 5L, "water", 3000L), plan.usedInventory());
        assertEquals(Map.of("ore", 1L), plan.missing());
        assertSame(pattern, program.patternsByOutput().get("clean"));
        assertEquals(3, program.patternsByOutput().get("clean").outputs().size());
        assertEquals(-1, program.indexOf("sludge"));
    }

    @Test
    void requestingTheSecondaryOutputUsesItsOwnYield() {
        var program = compile(List.of(process("separator", List.of(slot("raw", 3)),
                Map.of("main", 7L, "gas", 2L))), "gas");
        var plan = program.planLong(5, program.captureLongInventory(ignored -> 0), PlanningGuard.none());
        assertEquals(Map.of("separator", 3L), plan.patternExecutions());
        assertEquals(Map.of("raw", 9L), plan.missing());
    }

    @Test
    void rejectsAByproductConsumedInAnotherBranchInsteadOfInventingInventory() {
        var root = process("assemble", List.of(slot("part", 1), slot("gas", 2)), Map.of("out", 1L));
        var split = process("split", List.of(slot("raw", 1)), Map.of("part", 1L, "gas", 2L));
        var result = CompiledRootProgram.compile(CompiledCraftingGraph.compile(1, List.of(root, split)),
                "out", ignored -> false);
        assertTrue(result.program().isEmpty());
        assertEquals("COUPLED_OUTPUTS", result.failure().name());
    }

    @Test
    void detectsOverlapEvenWhenTheOtherBranchHasAnEmitter() {
        var root = process("assemble", List.of(slot("part", 1), slot("gas", 2)), Map.of("out", 1L));
        var split = process("split", List.of(slot("raw", 1)), Map.of("part", 1L, "gas", 2L));
        var result = CompiledRootProgram.compile(CompiledCraftingGraph.compile(1, List.of(root, split)),
                "out", "gas"::equals);
        assertTrue(result.program().isEmpty());
    }

    @Test
    void promotesWhenOnlyTheUnrequestedByproductExceedsLong() {
        var program = compile(List.of(process("split", List.of(slot("raw", 1)),
                Map.of("part", 1L, "gas", Long.MAX_VALUE))), "part");
        var result = new OverflowPromotingCraftingPlanner<String>().plan(program, BigInteger.TWO,
                program.captureLongInventory(ignored -> 0), PlanningGuard.none());
        var big = assertInstanceOf(OverflowPromotingCraftingPlanner.BigResult.class, result);
        assertEquals(BigInteger.TWO, big.plan().patternExecutions().get("split"));
        assertEquals(Map.of("raw", BigInteger.TWO), big.plan().missing());
        assertTrue(WideArithmeticPreflight.requiresWideArithmetic("part", BigInteger.TWO,
                program, ignored -> 1, BITS, PlanningGuard.none()));
    }

    @Test
    void catchesOutputAggregateOverflowAcrossDifferentPatterns() {
        long half = Long.MAX_VALUE / 2;
        var root = process("root", List.of(slot("a", 1), slot("b", 1)), Map.of("out", 1L));
        var a = process("a", List.of(slot("raw-a", 1)), Map.of("a", 1L, "waste", half));
        var b = process("b", List.of(slot("raw-b", 1)), Map.of("b", 1L, "waste", half));
        var program = compile(List.of(root, a, b), "out");
        assertInstanceOf(OverflowPromotingCraftingPlanner.BigResult.class,
                new OverflowPromotingCraftingPlanner<String>().plan(program, BigInteger.ONE,
                        program.captureLongInventory(ignored -> 0), PlanningGuard.none()));
    }

    @Test
    void outputMagnitudeLimitsAreCheckedEvenWithoutInputOverflow() {
        var program = compile(List.of(process("split", List.of(slot("raw", 1)),
                Map.of("part", 1L, "gas", Long.MAX_VALUE))), "part");
        assertThrows(IllegalArgumentException.class, () -> program.planBig(BigInteger.ONE.shiftLeft(70),
                program.captureBigInventory(ignored -> BigInteger.ZERO, 128), PlanningGuard.none(), 128));
    }

    @Test
    void longWindowAccountsForAllOutputsNotJustRequestedItems() {
        var program = compile(List.of(process("split", List.of(slot("raw", 1)),
                Map.of("part", 1L, "gas", 1L << 61))), "part");
        var window = Ae2BigCraftingPlanFactory.rootWindowDecision(program, BigInteger.TEN, 10, BITS);
        assertEquals(Ae2BigCraftingPlanFactory.ExecutionMode.ROOT_WINDOWS, window.mode());
        assertEquals(3, window.maximumRootExecutions());
    }

    @Test
    void byproductChangesInvalidateTheProgramFingerprint() {
        var a = compile(List.of(process("stable-id", List.of(slot("raw", 1)),
                Map.of("out", 1L, "waste", 2L))), "out");
        var b = compile(List.of(process("stable-id", List.of(slot("raw", 1)),
                Map.of("out", 1L, "waste", 3L))), "out");
        assertNotEquals(Ae2BigCraftingPlanFactory.computeProgramFingerprint(a, key -> key),
                Ae2BigCraftingPlanFactory.computeProgramFingerprint(b, key -> key));
        var reordered = new LinkedHashMap<String, Long>();
        reordered.put("waste", 2L);
        reordered.put("out", 1L);
        var c = compile(List.of(process("stable-id", List.of(slot("raw", 1)), reordered)), "out");
        assertEquals(Ae2BigCraftingPlanFactory.computeProgramFingerprint(a, key -> key),
                Ae2BigCraftingPlanFactory.computeProgramFingerprint(c, key -> key));
    }

    @Test
    void cancellingMultiOutputPlanningDoesNotPublishAPartialPlan() {
        var program = compile(List.of(process("split", List.of(slot("raw", 1)),
                Map.of("out", 1L, "waste", 2L))), "out");
        assertThrows(PlanningCancelledException.class, () -> program.planLong(10,
                program.captureLongInventory(ignored -> 0), visited -> { throw new PlanningCancelledException(visited); }));
    }

    @Test
    void craftingOnlyExactApiCannotLoseASecondOutput() {
        var pattern = new CompiledPattern<>("craft", List.of(slot("raw", 1)),
                Map.of("out", 1L, "extra", 1L), false);
        var program = compile(List.of(pattern), "out");
        assertTrue(program.tryPlanDeterministicCraftingBig(BigInteger.ONE,
                program.captureBigInventory(key -> key.equals("raw") ? BigInteger.ONE : BigInteger.ZERO, BITS), BITS)
                .isEmpty());
    }

    @Test
    void longSafetyCertificateCannotHideAByproductOverflowOnALargerOrder() {
        var program = compile(List.of(process("split", List.of(slot("raw", 1)),
                Map.of("out", 1L, "gas", 1L << 61))), "out");
        var certificate = WideArithmeticPreflight.longSafetyCertificate(program, ignored -> 1, BITS);
        assertTrue(certificate.certify(BigInteger.ONE));
        assertFalse(certificate.certify(BigInteger.valueOf(4)));
        assertFalse(certificate.certifiesCached(BigInteger.valueOf(4)));
        assertTrue(WideArithmeticPreflight.requiresWideArithmetic("out", BigInteger.valueOf(4),
                program, ignored -> 1, BITS, PlanningGuard.none()));
    }

    private static CompiledPattern.InputSlot<String> slot(String key, long amount) {
        return new CompiledPattern.InputSlot<>(List.of(new CompiledPattern.Stack<>(key, amount)));
    }

    private static CompiledPattern<String> process(String id, List<CompiledPattern.InputSlot<String>> inputs,
            Map<String, Long> outputs) {
        return new CompiledPattern<>(id, inputs, outputs, true);
    }

    private static CompiledRootProgram<String> compile(List<CompiledPattern<String>> patterns, String root) {
        return CompiledRootProgram.tryCompile(CompiledCraftingGraph.compile(1, patterns), root, ignored -> false)
                .orElseThrow();
    }
}

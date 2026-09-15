package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReusableByproductPlanningTest {
    @Test
    void siblingInputsReuseOneProcessingOperation() {
        var root = process("assemble", List.of(slot("a", 1), slot("b", 1)), Map.of("out", 1L));
        var split = process("split", List.of(slot("raw", 1)), Map.of("a", 1L, "b", 1L));
        var program = compile(root, split);
        var plan = program.planLong(10, program.captureLongInventory(k -> k.equals("raw") ? 10 : 0),
                PlanningGuard.none());
        assertEquals(Map.of("assemble", 10L, "split", 10L), plan.patternExecutions());
        assertEquals(Map.of("raw", 10L), plan.usedInventory());
        assertTrue(plan.missing().isEmpty());
    }

    @Test
    void priorByproductsReduceInitialReservationInsteadOfBeingReservedAgain() {
        var root = process("assemble", List.of(slot("a", 1), slot("b", 3)), Map.of("out", 1L));
        var split = process("split", List.of(slot("raw", 1)), Map.of("a", 1L, "b", 2L));
        var program = compile(root, split);
        var plan = program.planLong(2, program.captureLongInventory(k -> switch(k) {
            case "raw" -> 2;
            case "b" -> 10;
            default -> 0;
        }), PlanningGuard.none());
        assertEquals(Map.of("raw", 2L, "b", 2L), plan.usedInventory());
        assertEquals(Map.of("assemble", 2L, "split", 2L), plan.patternExecutions());
    }

    @Test
    void hugeOrdersDoNotExpandPerItem() {
        var root = process("assemble", List.of(slot("a", 1), slot("b", 2)), Map.of("out", 1L));
        var split = process("split", List.of(slot("raw", 3)), Map.of("a", 1L, "b", 2L));
        var program = compile(root, split);
        var amount = BigInteger.TEN.pow(64);
        var plan = program.planBig(amount, program.captureBigInventory(k -> BigInteger.ZERO, 4096),
                PlanningGuard.none(), 4096);
        assertEquals(Map.of("assemble", amount, "split", amount), plan.patternExecutions());
        assertEquals(Map.of("raw", amount.multiply(BigInteger.valueOf(3))), plan.missing());
        assertTrue(plan.expandedRequests() < 10);
    }

    @Test
    void outputOverflowCountsASharedProducerOnlyOnce() {
        var root = process("assemble", List.of(slot("a", 1), slot("b", 1)), Map.of("out", 1L));
        var split = process("split", List.of(slot("raw", 1)),
                Map.of("a", 1L, "b", 1L, "waste", Long.MAX_VALUE - 3));
        var program = compile(root, split);
        var inventory = program.captureLongInventory(k -> 0);
        assertEquals(1, program.planLong(1, inventory, PlanningGuard.none()).patternExecutions().get("split"));
        assertThrows(CountOverflowException.class, () -> program.planLong(2, inventory, PlanningGuard.none()));
        var promoted = new OverflowPromotingCraftingPlanner<String>().plan(program, BigInteger.TWO,
                inventory, PlanningGuard.none());
        assertInstanceOf(OverflowPromotingCraftingPlanner.BigResult.class, promoted);
    }

    @Test
    void cancellationAndRepeatPlanningLeaveCapturedInventoryUntouched() {
        var program = compile(process("assemble", List.of(slot("a", 1), slot("b", 1)), Map.of("out", 1L)),
                process("split", List.of(slot("raw", 1)), Map.of("a", 1L, "b", 1L)));
        var inventory = program.captureLongInventory(k -> k.equals("raw") ? 10 : 0);
        var first = program.planLong(10, inventory, PlanningGuard.none());
        assertThrows(PlanningCancelledException.class, () -> program.planLong(10, inventory, visited -> {
            if (visited >= 2) throw new PlanningCancelledException(visited);
        }));
        assertEquals(first, program.planLong(10, inventory, PlanningGuard.none()));
        assertThrows(UnsupportedOperationException.class, () -> first.trace().charges().clear());
    }

    @Test
    void coupledPlansDoNotReuseAnUnprovenMonotonicLongCertificate() {
        var program = compile(process("assemble", List.of(slot("a", 1), slot("b", 1)), Map.of("out", 1L)),
                process("split", List.of(slot("raw", 1)), Map.of("a", 1L, "b", 1L)));
        var certificate = WideArithmeticPreflight.longSafetyCertificate(program, k -> 1, 4096);
        assertFalse(certificate.certify(BigInteger.ONE));
        certificate.recordExactSafe(BigInteger.TEN);
        assertFalse(certificate.certifiesCached(BigInteger.ONE));
        assertFalse(WideArithmeticPreflight.requiresWideArithmetic("out", BigInteger.TEN,
                program, k -> 1, 4096, PlanningGuard.none()));
    }

    @Test
    void templateQuantumIsIncludedInTheProgramFingerprint() {
        var split = process("split", List.of(slot("raw", 1)), Map.of("a", 1L, "water", 1000L));
        var one = compile(process("stable", List.of(slot("a", 1), slot("water", 1000)), Map.of("out", 1L)), split);
        var bucket = compile(process("stable", List.of(slot("a", 1), new CompiledPattern.InputSlot<>(
                List.of(new CompiledPattern.Stack<>("water", 1000)), 1000)), Map.of("out", 1L)), split);
        assertNotEquals(Ae2BigCraftingPlanFactory.computeProgramFingerprint(one, k -> k),
                Ae2BigCraftingPlanFactory.computeProgramFingerprint(bucket, k -> k));
    }

    @Test
    void legacyIndependentFingerprintsRemainStable() {
        var one = compile(process("stable", List.of(slot("water", 1000)), Map.of("out", 1L)));
        var bucket = compile(process("stable", List.of(new CompiledPattern.InputSlot<>(
                List.of(new CompiledPattern.Stack<>("water", 1000)), 1000)), Map.of("out", 1L)));
        assertEquals(Ae2BigCraftingPlanFactory.computeProgramFingerprint(one, k -> k),
                Ae2BigCraftingPlanFactory.computeProgramFingerprint(bucket, k -> k));
    }

    @Test
    void oldPublicConstructorsStillRepresentUntracedPlans() {
        assertNull(new LongCraftingPlan<>("out", 1, Map.of(), Map.of(), Map.of(), Map.of()).trace());
        assertNull(new BigCraftingPlan<>("out", BigInteger.ONE, Map.of(), Map.of(), Map.of(), Map.of(), 1).trace());
    }

    @SafeVarargs
    static CompiledRootProgram<String> compile(CompiledPattern<String>... patterns) {
        return CompiledRootProgram.tryCompile(CompiledCraftingGraph.compile(1, List.of(patterns)),
                "out", k -> false).orElseThrow();
    }

    static CompiledPattern.InputSlot<String> slot(String key, long amount) {
        return new CompiledPattern.InputSlot<>(List.of(new CompiledPattern.Stack<>(key, amount)));
    }

    static CompiledPattern<String> process(String id, List<CompiledPattern.InputSlot<String>> inputs,
            Map<String, Long> outputs) {
        return new CompiledPattern<>(id, inputs, outputs, true);
    }
}

package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegacyRootStockMatrixTest {
    private static final String ROOT = "creative";
    private static final String MID = "illusion";
    private static final String RAW_A = "quantum_core";
    private static final String RAW_B = "alloy";

    @Test
    void legacyPublicRootProgramRetainsItsExactStockMatrix() {
        CompiledRootProgram<String> program = eligibleProgram();

        for (BigInteger request : List.of(
                BigInteger.ONE,
                BigInteger.TWO,
                BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                BigInteger.TEN.pow(64))) {
            BigInteger midDemand = request.multiply(BigInteger.valueOf(16));
            BigInteger rawADemand = midDemand.multiply(BigInteger.TWO);
            BigInteger rawBDemand = request.multiply(BigInteger.valueOf(16));
            for (int stockCase = 0; stockCase < 3; stockCase++) {
                BigInteger rawAStock = stockCase == 0 ? BigInteger.ZERO
                        : stockCase == 1 ? rawADemand.divide(BigInteger.TWO) : rawADemand;
                BigInteger rawBStock = stockCase == 0 ? BigInteger.ZERO
                        : stockCase == 1 ? rawBDemand.divide(BigInteger.TWO) : rawBDemand;
                var inventory = program.captureBigInventory(key -> switch (key) {
                    case RAW_A -> rawAStock;
                    case RAW_B -> rawBStock;
                    default -> BigInteger.ZERO;
                }, BigCountMath.HARD_MAXIMUM_BITS);

                BigCraftingPlan<String> result = program.planBig(
                        request, inventory, PlanningGuard.none(), BigCountMath.HARD_MAXIMUM_BITS);

                assertEquals(request, result.patternExecutions().get("root"));
                assertEquals(midDemand, result.patternExecutions().get("mid"));
                assertEquals(rawAStock.signum() == 0 ? null : rawAStock,
                        result.usedInventory().get(RAW_A));
                assertEquals(rawBStock.signum() == 0 ? null : rawBStock,
                        result.usedInventory().get(RAW_B));
                assertEquals(rawADemand.subtract(rawAStock).signum() == 0
                                ? null : rawADemand.subtract(rawAStock),
                        result.missing().get(RAW_A));
                assertEquals(rawBDemand.subtract(rawBStock).signum() == 0
                                ? null : rawBDemand.subtract(rawBStock),
                        result.missing().get(RAW_B));
                assertTrue(result.emitted().isEmpty());
            }
        }
    }

    @Test
    void finishedRootStockAvoidsEveryPatternExecution() {
        CompiledRootProgram<String> program = eligibleProgram();
        var inventory = program.captureBigInventory(
                key -> key.equals(ROOT) ? BigInteger.ONE : BigInteger.ZERO,
                BigCountMath.HARD_MAXIMUM_BITS);
        BigCraftingPlan<String> result = program.planBig(
                BigInteger.ONE, inventory, PlanningGuard.none(), BigCountMath.HARD_MAXIMUM_BITS);
        assertEquals(Map.of(ROOT, BigInteger.ONE), result.usedInventory());
        assertTrue(result.patternExecutions().isEmpty());
        assertTrue(result.missing().isEmpty());
    }

    @Test
    void legacySignedLongPlanningStillWorks() {
        CompiledRootProgram<String> program = eligibleProgram();
        var inventory = program.captureLongInventory(ignored -> 0L);
        LongCraftingPlan<String> result = program.planLong(
                2L, inventory, PlanningGuard.none());
        assertEquals(2L, result.patternExecutions().get("root"));
        assertEquals(32L, result.patternExecutions().get("mid"));
        assertEquals(64L, result.missing().get(RAW_A));
        assertEquals(32L, result.missing().get(RAW_B));
    }

    private static CompiledRootProgram<String> eligibleProgram() {
        var root = new CompiledPattern<>("root",
                List.of(slot(MID, 16), slot(RAW_B, 16)), Map.of(ROOT, 1L), false);
        var mid = new CompiledPattern<>("mid",
                List.of(slot(RAW_A, 2)), Map.of(MID, 1L), false);
        return CompiledRootProgram.tryCompile(
                CompiledCraftingGraph.compile(1, List.of(root, mid)), ROOT, ignored -> false)
                .orElseThrow();
    }

    private static CompiledPattern.InputSlot<String> slot(String key, long amount) {
        return new CompiledPattern.InputSlot<>(
                List.of(new CompiledPattern.Stack<>(key, amount)));
    }
}

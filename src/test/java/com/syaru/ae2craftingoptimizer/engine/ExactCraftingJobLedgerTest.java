package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExactCraftingJobLedgerTest {
    private static final BigInteger ABOVE_LONG =
            BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);

    @Test
    void keepsBigIntegerTasksWhileProjectingLongFacade() {
        ExactCraftingJobLedger<String, String> ledger =
                ExactCraftingJobLedger.planned(
                        Map.of("pattern", ABOVE_LONG),
                        Map.of(),
                        ABOVE_LONG);

        assertEquals(ABOVE_LONG, ledger.remainingTasks().get("pattern"));
        assertEquals(
                Long.MAX_VALUE,
                ExactCraftingJobLedger.saturatedLong(
                        ledger.remainingTasks().get("pattern")));
    }

    @Test
    void longAndBigIntegerAmountsUseTheSameThreeCounterLifecycle() {
        assertThreeCounterLifecycle(
                BigInteger.valueOf(64));
        assertThreeCounterLifecycle(
                ABOVE_LONG);
    }

    @Test
    void replayingTheSameAbsoluteReceiptDoesNotDoubleAccount() {
        ExactCraftingJobLedger<String, String> ledger =
                ExactCraftingJobLedger.planned(
                        Map.of("pattern", ABOVE_LONG),
                        Map.of(),
                        ABOVE_LONG);
        Map<String, BigInteger> dispatched =
                Map.of("pattern", BigInteger.valueOf(500));
        Map<String, BigInteger> credited =
                Map.of("result", BigInteger.valueOf(500));

        ledger.reconcile(
                dispatched,
                Map.of("result", ABOVE_LONG),
                credited,
                ABOVE_LONG);
        ledger.reconcile(
                dispatched,
                Map.of("result", ABOVE_LONG),
                credited,
                ABOVE_LONG);

        assertEquals(
                ABOVE_LONG.subtract(BigInteger.valueOf(500)),
                ledger.remainingTasks().get("pattern"));
        assertEquals(
                Map.of(
                        "result",
                        ABOVE_LONG.subtract(BigInteger.valueOf(500))),
                ledger.waitingFor());
    }

    @Test
    void addsWaitingOutputsOnlyAfterTheirPatternWasDispatched() {
        ExactCraftingJobLedger<String, String> ledger =
                ExactCraftingJobLedger.planned(
                        Map.of("pattern", ABOVE_LONG),
                        Map.of(),
                        BigInteger.ONE);

        assertEquals(Map.of(), ledger.waitingFor());

        ledger.reconcile(
                Map.of("pattern", ABOVE_LONG),
                Map.of("result", ABOVE_LONG),
                Map.of(),
                BigInteger.ONE);

        assertEquals(
                Map.of("result", ABOVE_LONG),
                ledger.waitingFor());
    }

    @Test
    void rejectsBackwardOrExcessProgress() {
        ExactCraftingJobLedger<String, String> ledger =
                ExactCraftingJobLedger.planned(
                        Map.of("pattern", BigInteger.TEN),
                        Map.of(),
                        BigInteger.TEN);
        ledger.reconcile(
                Map.of("pattern", BigInteger.valueOf(5)),
                Map.of("result", BigInteger.valueOf(5)),
                Map.of("result", BigInteger.valueOf(5)),
                BigInteger.TEN);

        assertThrows(
                IllegalStateException.class,
                () -> ledger.reconcile(
                        Map.of("pattern", BigInteger.valueOf(4)),
                        Map.of("result", BigInteger.valueOf(5)),
                        Map.of("result", BigInteger.valueOf(5)),
                        BigInteger.TEN));
        assertThrows(
                IllegalArgumentException.class,
                () -> ledger.reconcile(
                        Map.of("pattern", BigInteger.valueOf(11)),
                        Map.of("result", BigInteger.valueOf(5)),
                        Map.of("result", BigInteger.valueOf(5)),
                        BigInteger.TEN));
        assertThrows(
                IllegalStateException.class,
                () -> ledger.reconcile(
                        Map.of("pattern", BigInteger.valueOf(5)),
                        Map.of("result", BigInteger.valueOf(4)),
                        Map.of("result", BigInteger.valueOf(4)),
                        BigInteger.TEN));
        assertThrows(
                IllegalArgumentException.class,
                () -> ledger.reconcile(
                        Map.of("pattern", BigInteger.valueOf(5)),
                        Map.of("result", BigInteger.valueOf(5)),
                        Map.of("result", BigInteger.valueOf(11)),
                        BigInteger.TEN));
    }

    @Test
    void completesOnlyAfterAllThreeCountersReachTheirTerminalState() {
        ExactCraftingJobLedger<String, String> ledger =
                ExactCraftingJobLedger.planned(
                        Map.of("pattern", ABOVE_LONG),
                        Map.of(),
                        ABOVE_LONG);

        ledger.reconcile(
                Map.of("pattern", ABOVE_LONG),
                Map.of("result", ABOVE_LONG),
                Map.of(),
                ABOVE_LONG);
        assertFalse(ledger.completeAndBalanced());

        ledger.reconcile(
                Map.of("pattern", ABOVE_LONG),
                Map.of("result", ABOVE_LONG),
                Map.of("result", ABOVE_LONG),
                ABOVE_LONG);
        assertFalse(ledger.completeAndBalanced());

        ledger.reconcile(
                Map.of("pattern", ABOVE_LONG),
                Map.of("result", ABOVE_LONG),
                Map.of("result", ABOVE_LONG),
                BigInteger.ZERO);

        assertTrue(ledger.completeAndBalanced());
        assertEquals(Map.of(), ledger.remainingTasks());
        assertEquals(Map.of(), ledger.waitingFor());
        assertEquals(BigInteger.ZERO, ledger.remainingOutput());
    }

    @Test
    void restoresTheSameAbsoluteProgressWithoutChangingCounts() {
        BigInteger dispatched =
                ABOVE_LONG.subtract(BigInteger.TEN);
        BigInteger introduced =
                ABOVE_LONG.multiply(BigInteger.TWO);
        BigInteger credited =
                introduced.subtract(BigInteger.ONE);
        ExactCraftingJobLedger<String, String> restored =
                new ExactCraftingJobLedger<>(
                        Map.of("pattern", ABOVE_LONG),
                        Map.of(),
                        Map.of("pattern", dispatched),
                        Map.of("result", introduced),
                        Map.of("result", credited),
                        ABOVE_LONG,
                        BigInteger.ONE);

        assertEquals(
                Map.of("pattern", BigInteger.TEN),
                restored.remainingTasks());
        assertEquals(
                Map.of("result", BigInteger.ONE),
                restored.waitingFor());
        assertEquals(
                BigInteger.ONE,
                restored.remainingOutput());
    }

    private static void assertThreeCounterLifecycle(
            BigInteger total) {
        ExactCraftingJobLedger<String, String> ledger =
                ExactCraftingJobLedger.planned(
                        Map.of("pattern", total),
                        Map.of(),
                        total);

        ledger.reconcile(
                Map.of("pattern", total),
                Map.of("result", total),
                Map.of(),
                total);
        assertEquals(Map.of(), ledger.remainingTasks());
        assertEquals(Map.of("result", total), ledger.waitingFor());
        assertEquals(total, ledger.remainingOutput());
        assertFalse(ledger.completeAndBalanced());

        ledger.reconcile(
                Map.of("pattern", total),
                Map.of("result", total),
                Map.of("result", total),
                BigInteger.ZERO);
        assertEquals(Map.of(), ledger.remainingTasks());
        assertEquals(Map.of(), ledger.waitingFor());
        assertEquals(BigInteger.ZERO, ledger.remainingOutput());
        assertTrue(ledger.completeAndBalanced());
    }
}

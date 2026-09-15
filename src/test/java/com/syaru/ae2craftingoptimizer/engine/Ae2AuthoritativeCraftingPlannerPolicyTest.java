package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syaru.ae2craftingoptimizer.optimization.BigIntegerPlanDeclineReason;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class Ae2AuthoritativeCraftingPlannerPolicyTest {
    @Test
    void retainsLongFacadeWhenFullExpansionNeedsWideArithmetic() {
        assertTrue(Ae2AuthoritativeCraftingPlanner.shouldRetainLongFacade(
                false,
                true));
    }

    @Test
    void leavesOrdinaryLongPlanOnAe2WhenAuthoritativePlannerIsDisabled() {
        assertFalse(Ae2AuthoritativeCraftingPlanner.shouldRetainLongFacade(
                false,
                false));
    }

    @Test
    void retainsOrdinaryLongPlanWhenAuthoritativePlannerIsEnabled() {
        assertTrue(Ae2AuthoritativeCraftingPlanner.shouldRetainLongFacade(
                true,
                false));
    }

    @Test
    void acceptsStrictlyProvenLongPlanWithoutShadowHistory() {
        assertTrue(Ae2AuthoritativeCraftingPlanner.isQualifiedForReplacement(
                false,
                true,
                false,
                false));
    }

    @Test
    void rejectsOrdinaryLongPlanWhenNeitherProofNorShadowQualifiesIt() {
        assertFalse(Ae2AuthoritativeCraftingPlanner.isQualifiedForReplacement(
                false,
                false,
                false,
                false));
    }

    @Test
    void reportsSnapshotFailureSeparatelyFromAmbiguousProducer() {
        assertEquals(
                BigIntegerPlanDeclineReason.INCOMPLETE_GRAPH_SNAPSHOT,
                Ae2AuthoritativeCraftingPlanner.classifyRootProgramFailure(
                        RootProgramFailure.INCOMPLETE_PATTERN_SNAPSHOT));
        assertEquals(
                BigIntegerPlanDeclineReason.INCOMPLETE_GRAPH_SNAPSHOT,
                Ae2AuthoritativeCraftingPlanner.classifyRootProgramFailure(
                        RootProgramFailure.MISSING_FROM_SNAPSHOT));
    }

    @Test
    void honorsWidePlanShadowRequirement() {
        assertFalse(Ae2AuthoritativeCraftingPlanner.isQualifiedForReplacement(
                false,
                true,
                true,
                true));
        assertTrue(Ae2AuthoritativeCraftingPlanner.isQualifiedForReplacement(
                false,
                false,
                true,
                false));
    }

    @Test
    void ordinaryStaleSnapshotFallsBackWithoutRelabelingInventory() {
        assertEquals(
                Ae2AuthoritativeCraftingPlanner.StaleSnapshotAction.FALLBACK_TO_AE2,
                Ae2AuthoritativeCraftingPlanner.staleSnapshotAction(
                        false,
                        false));
    }

    @Test
    void capturesExactInventoryOnlyAfterWideArithmeticIsConfirmed() {
        var unassessed = Ae2AuthoritativeCraftingPlanner.ArithmeticCaptureMode.UNASSESSED;
        var exact = Ae2AuthoritativeCraftingPlanner.ArithmeticCaptureMode.EXACT_AVAILABLE;

        assertFalse(unassessed.requiresDeferredExactInventory(false));
        assertTrue(unassessed.requiresDeferredExactInventory(true));
        assertFalse(exact.requiresDeferredExactInventory(true));
    }

    @Test
    void fallsBackToAe2AfterARepeatedOrdinaryStaleSnapshot() {
        assertEquals(
                Ae2AuthoritativeCraftingPlanner.StaleSnapshotAction.FALLBACK_TO_AE2,
                Ae2AuthoritativeCraftingPlanner.staleSnapshotAction(
                        false,
                        false));
    }

    @Test
    void neverFallsBackAProvenWidePlanToLongArithmetic() {
        assertEquals(
                Ae2AuthoritativeCraftingPlanner.StaleSnapshotAction.REJECT_WIDE,
                Ae2AuthoritativeCraftingPlanner.staleSnapshotAction(
                        false,
                        true));
    }

    @Test
    void cancellationTakesPriorityOverSnapshotRetry() {
        assertEquals(
                Ae2AuthoritativeCraftingPlanner.StaleSnapshotAction.CANCEL,
                Ae2AuthoritativeCraftingPlanner.staleSnapshotAction(
                        true,
                        false));
    }

    @Test
    void yieldsToAe2BeforeWaitingForServerThreadExactCapture() {
        CompletableFuture<String> exactCapture = new CompletableFuture<>();
        AtomicInteger yields = new AtomicInteger();

        Ae2AuthoritativeCraftingPlanner.cooperativelyAwait(
                exactCapture,
                () -> {
                    yields.incrementAndGet();
                    exactCapture.complete("captured");
                },
                true);

        assertEquals(1, yields.get());
        assertEquals("captured", exactCapture.join());
    }

    @Test
    void refusesPendingServerCaptureWithoutAe2YieldHandshake() {
        CompletableFuture<String> exactCapture = new CompletableFuture<>();

        assertThrows(
                IllegalStateException.class,
                () -> Ae2AuthoritativeCraftingPlanner.cooperativelyAwait(
                        exactCapture,
                        null,
                        true));
        assertTrue(exactCapture.isCancelled());
    }
}

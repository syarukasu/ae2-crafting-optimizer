package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void ordinaryStaleSnapshotRefreshesWithoutRelabelingInventory() {
        assertEquals(
                Ae2AuthoritativeCraftingPlanner.StaleSnapshotAction.REFRESH_CAPTURE,
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
    void repeatedStaleCaptureIsBoundedAndDoesNotReturnAFallback() {
        AtomicInteger plans = new AtomicInteger();
        AtomicInteger captures = new AtomicInteger();
        StalePlanningSnapshotException stale = new StalePlanningSnapshotException(
                new PlanningGenerationSnapshot(1, 1, 1), 0);
        assertEquals(stale, assertThrows(StalePlanningSnapshotException.class,
                () -> Ae2AuthoritativeCraftingPlanner.retryStalePlan("initial",
                        snapshot -> { plans.incrementAndGet(); throw stale; },
                        snapshot -> "fresh-" + captures.incrementAndGet())));
        assertEquals(3, plans.get());
        assertEquals(2, captures.get());
    }

    @Test
    void retryUsesFreshSnapshotAndDoesNotSwallowOtherFailures() {
        AtomicInteger captures = new AtomicInteger();
        StalePlanningSnapshotException stale = new StalePlanningSnapshotException(
                new PlanningGenerationSnapshot(1, 1, 1), 0);
        String result = Ae2AuthoritativeCraftingPlanner.retryStalePlan("initial", snapshot -> {
            if (snapshot.equals("initial")) throw stale;
            return snapshot;
        }, old -> "fresh-" + captures.incrementAndGet());
        assertEquals("fresh-1", result);
        assertEquals(1, captures.get());
        assertThrows(PlanningCancelledException.class,
                () -> Ae2AuthoritativeCraftingPlanner.retryStalePlan("initial",
                        snapshot -> { throw new PlanningCancelledException(0); },
                        old -> { captures.incrementAndGet(); return "wrong"; }));
        assertEquals(1, captures.get());
    }

    @Test
    void resumedDetachedWorkerCooperatesWhenRecapturing() throws Exception {
        AtomicInteger yields = new AtomicInteger();
        var detached = Ae2AuthoritativeCraftingPlanner.detachedWorkerYield(yields::incrementAndGet);
        assertFalse(detached.waitsForServerTick());
        detached.yieldToServerThread();
        assertEquals(0, yields.get());
        detached.beforeResult();
        assertTrue(detached.waitsForServerTick());
        detached.beforeResult();
        assertEquals(1, yields.get());
        detached.yieldToServerThread();
        assertEquals(2, yields.get());
    }

    @Test
    void refreshedUnsupportedPlanCannotResumeVanillaWithOldInventory() {
        var stale = new StalePlanningSnapshotException(new PlanningGenerationSnapshot(1, 1, 1), 0);
        AtomicInteger captures = new AtomicInteger();
        assertEquals(stale, assertThrows(StalePlanningSnapshotException.class,
                () -> Ae2AuthoritativeCraftingPlanner.retryStalePlan("initial", snapshot -> {
                    if (snapshot.equals("initial")) throw stale;
                    return null;
                }, old -> "fresh-" + captures.incrementAndGet())));
        assertEquals(1, captures.get());
        assertNull(Ae2AuthoritativeCraftingPlanner.retryStalePlan("initial", snapshot -> null,
                old -> { throw new AssertionError("initial unsupported plans do not require recapture"); }));
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

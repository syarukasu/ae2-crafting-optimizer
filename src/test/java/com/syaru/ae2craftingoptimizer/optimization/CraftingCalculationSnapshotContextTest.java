package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syaru.ae2craftingoptimizer.access.StorageRevisionAccess;
import org.junit.jupiter.api.Test;

class CraftingCalculationSnapshotContextTest {
    @Test
    void carriesTheConstructorRevisionToTheSubmitBoundary() {
        TestOwner owner = new TestOwner();
        var token = new StorageRevisionTracker.RevisionToken(owner, owner.current);
        var revision = new CraftingCalculationSnapshotContext.CalculationRevision(
                token,
                2L,
                3L,
                PlanningConfigurationRevisionTracker.current(),
                null);

        CraftingCalculationSnapshotContext.begin();
        assertTrue(CraftingCalculationSnapshotContext.hasActiveFrame());
        assertTrue(CraftingCalculationSnapshotContext.matches(null));
        assertNull(CraftingCalculationSnapshotContext.actionSource(null));
        CraftingCalculationSnapshotContext.capture(revision);

        assertSame(revision, CraftingCalculationSnapshotContext.finish());
        assertEquals(2L, revision.patternGeneration());
        assertEquals(3L, revision.recipeGeneration());
        assertEquals(0, CraftingCalculationSnapshotContext.depth());
    }

    @Test
    void directConstructionWithoutAServiceFrameDoesNotLeakContext() {
        TestOwner owner = new TestOwner();

        CraftingCalculationSnapshotContext.capture(new CraftingCalculationSnapshotContext.CalculationRevision(
                new StorageRevisionTracker.RevisionToken(owner, owner.current),
                2L,
                3L,
                PlanningConfigurationRevisionTracker.current(),
                null));

        assertNull(CraftingCalculationSnapshotContext.finish());
    }

    @Test
    void missingConstructorCaptureFallsBackAndCleansTheFrame() {
        CraftingCalculationSnapshotContext.begin();

        assertNull(CraftingCalculationSnapshotContext.finish());
        assertEquals(0, CraftingCalculationSnapshotContext.depth());
    }

    @Test
    void failedConstructorContextDoesNotPoisonTheNextRequest() {
        TestOwner owner = new TestOwner();
        var revision = new CraftingCalculationSnapshotContext.CalculationRevision(
                new StorageRevisionTracker.RevisionToken(owner, owner.current),
                4L,
                5L,
                PlanningConfigurationRevisionTracker.current(),
                null);
        CraftingCalculationSnapshotContext.begin();

        CraftingCalculationSnapshotContext.begin();
        CraftingCalculationSnapshotContext.capture(revision);

        assertSame(revision, CraftingCalculationSnapshotContext.finish());
        assertEquals(0, CraftingCalculationSnapshotContext.depth());
    }

    @Test
    void unfinishedFrameCannotAttachToAnotherRequester() {
        Object originalRequester = new Object();
        Object unrelatedRequester = new Object();

        CraftingCalculationSnapshotContext.begin(originalRequester, null);

        assertTrue(CraftingCalculationSnapshotContext.hasActiveFrame());
        assertTrue(CraftingCalculationSnapshotContext.matches(originalRequester));
        assertNull(CraftingCalculationSnapshotContext.actionSource(unrelatedRequester));
        assertFalse(CraftingCalculationSnapshotContext.matches(unrelatedRequester));
        assertNull(CraftingCalculationSnapshotContext.finish());
    }

    @Test
    void rejectsZeroConfigurationRevision() {
        TestOwner owner = new TestOwner();
        var token = new StorageRevisionTracker.RevisionToken(owner, owner.current);

        assertThrows(
                IllegalArgumentException.class,
                () -> new CraftingCalculationSnapshotContext.CalculationRevision(
                        token,
                        1L,
                        1L,
                        0L,
                        null));
    }

    private static final class TestOwner implements StorageRevisionAccess {
        private long current = 1L;

        @Override
        public long aco$captureStorageRevision() {
            return current;
        }

        @Override
        public long aco$currentStorageRevision() {
            return current;
        }

        @Override
        public void aco$advanceStorageRevision() {
            current++;
        }
    }
}

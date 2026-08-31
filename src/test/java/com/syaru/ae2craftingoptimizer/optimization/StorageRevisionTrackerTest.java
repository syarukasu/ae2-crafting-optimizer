package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StorageRevisionTrackerTest {
    @Test
    void invalidatesOnlyTheAdvancedOwner() {
        StorageRevisionState first = new StorageRevisionState();
        StorageRevisionState second = new StorageRevisionState();
        TestOwner firstOwner = new TestOwner(first);
        TestOwner secondOwner = new TestOwner(second);
        var firstToken = new StorageRevisionTracker.RevisionToken(firstOwner, first.current());
        var secondToken = new StorageRevisionTracker.RevisionToken(secondOwner, second.current());

        first.advance();

        assertFalse(StorageRevisionTracker.isCurrent(firstToken));
        assertTrue(StorageRevisionTracker.isCurrent(secondToken));
    }

    @Test
    void unchangedObservedInventoryKeepsRevision() {
        StorageRevisionState state = new StorageRevisionState();
        long first = state.capture();
        long second = state.capture();

        assertTrue(first == second);
    }

    @Test
    void everyMutationAdvancesTheMonotonicRevision() {
        StorageRevisionState state = new StorageRevisionState();
        state.capture();
        long before = state.current();

        state.advance();
        state.advance();
        long observed = state.capture();

        assertTrue(observed == before + 2L);
    }

    @Test
    void mutationAfterCaptureCreatesANewRevision() {
        StorageRevisionState state = new StorageRevisionState();
        long before = state.capture();

        state.advance();

        assertTrue(state.capture() == before + 1L);
    }

    private record TestOwner(StorageRevisionState state)
            implements com.syaru.ae2craftingoptimizer.access.StorageRevisionAccess {
        @Override
        public long aco$captureStorageRevision() {
            return state.capture();
        }

        @Override
        public long aco$currentStorageRevision() {
            return state.current();
        }

        @Override
        public void aco$advanceStorageRevision() {
            state.advance();
        }
    }
}

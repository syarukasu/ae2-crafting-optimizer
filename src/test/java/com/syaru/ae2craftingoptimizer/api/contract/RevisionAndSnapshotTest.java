package com.syaru.ae2craftingoptimizer.api.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RevisionAndSnapshotTest {
    private static final ExactCountLimits LIMITS = ExactCountLimits.defaults();

    @Test
    void wakeupRegistrationIsExplicitlyClosable() {
        AtomicInteger calls = new AtomicInteger();
        RevisionWakeupListener listener = revision -> calls.incrementAndGet();
        try (RevisionWakeupRegistration ignored = RevisionWakeupApi.register(listener)) {
            RevisionWakeupApi.publish(revision(1));
            assertEquals(1, calls.get());
        }
        RevisionWakeupApi.publish(revision(2));
        assertEquals(1, calls.get());
    }

    @Test
    void snapshotStateAndRevisionAreExplicitAndImmutable() {
        CraftingTableBatchSnapshot snapshot = CraftingTableBatchSnapshot.of(
                3,
                SnapshotState.PAUSED,
                Map.of("minecraft:iron", BigInteger.ONE),
                LIMITS);
        assertEquals(SnapshotState.PAUSED, snapshot.state());
        assertEquals(BigInteger.ONE, snapshot.outputCounts().get("minecraft:iron"));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.outputCounts().put("minecraft:zinc", BigInteger.ONE));

        SnapshotRevisionTracker tracker = new SnapshotRevisionTracker();
        tracker.accept(snapshot);
        assertThrows(IllegalArgumentException.class,
                () -> tracker.accept(CraftingTableBatchSnapshot.of(
                        2, SnapshotState.ACTIVE, Map.of(), LIMITS)));
    }

    @Test
    void unknownProofIsNeverAnOrphanProof() {
        LiveTransactionProof unknown = LiveTransactionProof.unknown(
                "aco-runtime", 1, "tx-1", new byte[] {1}, LIMITS);
        LiveTransactionProof absent = LiveTransactionProof.absentConfirmed(
                "aco-runtime", 1, "tx-1", new byte[] {1}, LIMITS);
        assertEquals(LiveTransactionState.UNKNOWN, unknown.result());
        assertEquals(LiveTransactionState.ABSENT_CONFIRMED, absent.result());
        assertEquals(false, ReceiptOrphanPolicy.mayForget(unknown));
        assertEquals(true, ReceiptOrphanPolicy.mayForget(absent));
    }

    private static BatchTargetRevision revision(long value) {
        return new BatchTargetRevision(
                "target-1", "runtime-1", "tx-1", value, value, value, "active", LIMITS);
    }
}

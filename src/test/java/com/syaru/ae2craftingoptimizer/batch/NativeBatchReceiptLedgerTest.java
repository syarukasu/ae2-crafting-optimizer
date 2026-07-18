package com.syaru.ae2craftingoptimizer.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceipt;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class NativeBatchReceiptLedgerTest {
    @Test
    void persistsAcceptedReceiptIdempotently() {
        UUID id = UUID.randomUUID();
        NativeBatchReceipt pending = new NativeBatchReceipt(
                id, NativeBatchReceipt.State.PENDING, 64L, "pattern", 10L);
        NativeBatchReceiptLedger ledger = new NativeBatchReceiptLedger();

        assertTrue(ledger.prepare(pending));
        ledger.finish(id, NativeBatchReceipt.State.ACCEPTED, 11L);

        NativeBatchReceiptLedger restored = new NativeBatchReceiptLedger();
        restored.load(ledger.save());
        assertEquals(NativeBatchReceipt.State.ACCEPTED, restored.get(id).state());
        assertEquals(64L, restored.get(id).executions());
    }

    @Test
    void refusesConflictingTerminalState() {
        UUID id = UUID.randomUUID();
        NativeBatchReceiptLedger ledger = new NativeBatchReceiptLedger();
        ledger.prepare(new NativeBatchReceipt(id, NativeBatchReceipt.State.PENDING, 1L, "p", 0L));
        ledger.finish(id, NativeBatchReceipt.State.ACCEPTED, 1L);

        assertThrows(
                IllegalStateException.class,
                () -> ledger.finish(id, NativeBatchReceipt.State.REJECTED, 2L));
    }

    @Test
    void removesOnlyTerminalReceiptAfterJournalCompletion() {
        UUID id = UUID.randomUUID();
        NativeBatchReceiptLedger ledger = new NativeBatchReceiptLedger();
        assertTrue(ledger.prepare(new NativeBatchReceipt(
                id, NativeBatchReceipt.State.PENDING, 8L, "pattern", 1L)));

        assertFalse(ledger.removeTerminal(id));
        assertEquals(NativeBatchReceipt.State.PENDING, ledger.get(id).state());

        ledger.finish(id, NativeBatchReceipt.State.ACCEPTED, 2L);
        assertTrue(ledger.removeTerminal(id));
        assertEquals(null, ledger.get(id));
        assertFalse(ledger.removeTerminal(id));
    }

    @Test
    void rejectsTerminalReceiptAsPreparation() {
        NativeBatchReceiptLedger ledger = new NativeBatchReceiptLedger();
        assertThrows(
                IllegalArgumentException.class,
                () -> ledger.prepare(new NativeBatchReceipt(
                        UUID.randomUUID(),
                        NativeBatchReceipt.State.ACCEPTED,
                        4L,
                        "pattern",
                        1L)));
    }

    @Test
    void malformedSavedReceiptFailsClosed() {
        CompoundTag saved = new CompoundTag();
        saved.putInt("schema", 1);
        ListTag entries = new ListTag();
        CompoundTag malformed = new CompoundTag();
        malformed.putUUID("id", UUID.randomUUID());
        malformed.putString("state", "NOT_A_STATE");
        malformed.putLong("executions", 1L);
        malformed.putString("pattern", "pattern");
        entries.add(malformed);
        saved.put("entries", entries);

        NativeBatchReceiptLedger ledger = new NativeBatchReceiptLedger();
        ledger.load(saved);

        assertFalse(ledger.isHealthy());
        assertFalse(ledger.prepare(new NativeBatchReceipt(
                UUID.randomUUID(), NativeBatchReceipt.State.PENDING, 1L, "new", 1L)));
    }

    @Test
    void retainsRecentTerminalReceiptsBeforeReusingCapacity() {
        NativeBatchReceiptLedger ledger = new NativeBatchReceiptLedger();
        for (int index = 0; index < 256; index++) {
            UUID id = UUID.randomUUID();
            assertTrue(ledger.prepare(new NativeBatchReceipt(
                    id, NativeBatchReceipt.State.PENDING, 1L, "p" + index, 0L)));
            ledger.finish(id, NativeBatchReceipt.State.ACCEPTED, 1L);
        }

        assertFalse(ledger.prepare(new NativeBatchReceipt(
                UUID.randomUUID(), NativeBatchReceipt.State.PENDING, 1L, "too-soon", 2L)));
        assertTrue(ledger.prepare(new NativeBatchReceipt(
                UUID.randomUUID(), NativeBatchReceipt.State.PENDING, 1L, "after-retention", 12_001L)));
    }

    @Test
    void unknownNativeSchemaFailsClosedWithoutOverwritingPayload() {
        CompoundTag saved = new CompoundTag();
        saved.putInt("schema", 99);
        saved.putString("futureData", "keep-me");

        NativeBatchReceiptLedger ledger = new NativeBatchReceiptLedger();
        ledger.load(saved);

        assertFalse(ledger.isHealthy());
        assertFalse(ledger.isEmpty());
        assertEquals("keep-me", ledger.save().getString("futureData"));
    }

    @Test
    void acceptedEscrowReceiptPersistsItsExactPayloadDigest() {
        UUID id = UUID.randomUUID();
        NativeBatchReceipt accepted = new NativeBatchReceipt(
                id,
                NativeBatchReceipt.State.ACCEPTED,
                65_536L,
                "pattern",
                "0123456789abcdef",
                20L);
        NativeBatchReceiptLedger ledger = new NativeBatchReceiptLedger();

        assertTrue(ledger.acceptOwned(accepted));
        assertTrue(ledger.acceptOwned(accepted));

        NativeBatchReceiptLedger restored = new NativeBatchReceiptLedger();
        restored.load(ledger.save());
        assertEquals(accepted, restored.get(id));
        assertTrue(restored.get(id).hasDurablePayloadProof());
    }

    @Test
    void acceptedEscrowRejectsSameTransactionWithDifferentPayload() {
        UUID id = UUID.randomUUID();
        NativeBatchReceiptLedger ledger = new NativeBatchReceiptLedger();
        NativeBatchReceipt first = new NativeBatchReceipt(
                id, NativeBatchReceipt.State.ACCEPTED, 2L, "pattern", "digest-a", 1L);
        NativeBatchReceipt conflicting = new NativeBatchReceipt(
                id, NativeBatchReceipt.State.ACCEPTED, 2L, "pattern", "digest-b", 1L);

        assertTrue(ledger.acceptOwned(first));
        assertFalse(ledger.acceptOwned(conflicting));
        assertEquals(first, ledger.get(id));
    }

    @Test
    void schemaOneReceiptLoadsForQuarantineButCannotProveOwnership() {
        UUID id = UUID.randomUUID();
        CompoundTag saved = new CompoundTag();
        saved.putInt("schema", 1);
        ListTag entries = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putUUID("id", id);
        entry.putString("state", NativeBatchReceipt.State.ACCEPTED.name());
        entry.putLong("executions", 4L);
        entry.putString("pattern", "legacy");
        entry.putLong("updatedTick", 1L);
        entries.add(entry);
        saved.put("entries", entries);

        NativeBatchReceiptLedger ledger = new NativeBatchReceiptLedger();
        ledger.load(saved);

        assertTrue(ledger.isHealthy());
        assertFalse(ledger.get(id).hasDurablePayloadProof());
    }
}

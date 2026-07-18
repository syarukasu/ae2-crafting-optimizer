package com.syaru.ae2craftingoptimizer.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class BatchTransactionRecordTest {
    @Test
    void roundTripsJournalMetadataThroughNbt() {
        BatchTransactionRecord original = record();
        BatchTransactionRecord restored = BatchTransactionRecord.load(original.save());

        assertEquals(original.id(), restored.id());
        assertEquals(original.adapterId(), restored.adapterId());
        assertEquals(original.targetPos(), restored.targetPos());
        assertEquals(original.phase(), restored.phase());
        assertEquals(original.offeredExecutions(), restored.offeredExecutions());
    }

    @Test
    void rejectsAccountingBeforeTargetAcceptance() {
        assertThrows(
                IllegalStateException.class,
                () -> record().transition(
                        BatchTransactionPhase.ACCOUNTED,
                        4L,
                        2L,
                        new CompoundTag(),
                        "receipt"));
    }

    @Test
    void preservesQuarantinedRecordAcrossJournalReload() {
        BatchTransactionRecord quarantined = record().transition(
                BatchTransactionPhase.QUARANTINED,
                0L,
                2L,
                new CompoundTag(),
                "manual-review");
        CompoundTag saved = new CompoundTag();
        net.minecraft.nbt.ListTag entries = new net.minecraft.nbt.ListTag();
        entries.add(quarantined.save());
        saved.putInt("schema", 1);
        saved.put("transactions", entries);

        BatchTransactionJournal restored = BatchTransactionJournal.load(saved);

        assertEquals(1, restored.size());
        assertEquals(BatchTransactionPhase.QUARANTINED, restored.get(quarantined.id()).phase());
        assertEquals(0, restored.pending(10).size());
    }

    @Test
    void preservesMalformedEntriesAndStopsNewTransactions() {
        CompoundTag saved = new CompoundTag();
        saved.putInt("schema", 1);
        net.minecraft.nbt.ListTag entries = new net.minecraft.nbt.ListTag();
        CompoundTag malformed = record().save();
        malformed.remove("id");
        entries.add(malformed);
        saved.put("transactions", entries);

        BatchTransactionJournal restored = BatchTransactionJournal.load(saved);

        assertFalse(restored.isHealthy());
        assertEquals(1, restored.size());
        assertFalse(restored.putPrepared(record(), 16));
        assertEquals(1, restored.save(new CompoundTag())
                .getList("malformedTransactions", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .size());
    }

    @Test
    void unknownSchemaIsNotOverwritten() {
        CompoundTag saved = new CompoundTag();
        saved.putInt("schema", 99);
        saved.putString("futureData", "keep-me");

        BatchTransactionJournal restored = BatchTransactionJournal.load(saved);

        assertFalse(restored.isHealthy());
        assertFalse(restored.putPrepared(record(), 16));
        assertEquals("keep-me", restored.save(new CompoundTag()).getString("futureData"));
    }

    @Test
    void duplicateTransactionIdsFailClosedWithoutReplacingTheFirstRecord() {
        BatchTransactionRecord duplicate = record();
        CompoundTag saved = new CompoundTag();
        saved.putInt("schema", 1);
        net.minecraft.nbt.ListTag entries = new net.minecraft.nbt.ListTag();
        entries.add(duplicate.save());
        entries.add(duplicate.save());
        saved.put("transactions", entries);

        BatchTransactionJournal restored = BatchTransactionJournal.load(saved);

        assertFalse(restored.isHealthy());
        assertEquals(2, restored.size());
        assertEquals(duplicate.id(), restored.get(duplicate.id()).id());
        assertFalse(restored.putPrepared(record(), 16));
    }

    @Test
    void malformedListTypeFailsClosed() {
        CompoundTag malformed = record().save();
        malformed.putString("inputs", "not-a-list");

        assertThrows(IllegalArgumentException.class, () -> BatchTransactionRecord.load(malformed));
    }

    @Test
    void idempotentTransitionCannotChangeAcceptedCount() {
        BatchTransactionRecord prepared = record();
        assertThrows(
                IllegalStateException.class,
                () -> prepared.transition(
                        BatchTransactionPhase.PREPARED,
                        1L,
                        2L,
                        new CompoundTag(),
                        ""));
    }

    @Test
    void happyPathIsForwardOnlyAndPreservesAcceptedOwnership() {
        BatchTransactionRecord accepted = record().transition(
                BatchTransactionPhase.TARGET_ACCEPTED,
                4L,
                2L,
                new CompoundTag(),
                "receipt");
        BatchTransactionRecord accounted = accepted.transition(
                BatchTransactionPhase.ACCOUNTED,
                4L,
                3L,
                new CompoundTag(),
                "receipt");

        assertEquals(4L, accounted.acceptedExecutions());
        assertThrows(
                IllegalStateException.class,
                () -> accepted.transition(
                        BatchTransactionPhase.ROLLED_BACK,
                        0L,
                        3L,
                        new CompoundTag(),
                        ""));
    }

    private static BatchTransactionRecord record() {
        return new BatchTransactionRecord(
                UUID.randomUUID(),
                ResourceLocation.fromNamespaceAndPath("test", "adapter"),
                ResourceLocation.fromNamespaceAndPath("test", "source"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                new BlockPos(1, 2, 3),
                "pattern-fingerprint",
                4L,
                0L,
                1L,
                1L,
                BatchTransactionPhase.PREPARED,
                List.of(),
                List.of(),
                new CompoundTag(),
                new CompoundTag(),
                "");
    }
}

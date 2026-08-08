package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class ExactStorageMutationJournalTest {
    @Test
    void persistsPerStepProofAndAppliedMarker() {
        ExactStorageMutationJournal journal = new ExactStorageMutationJournal();
        UUID operation = UUID.randomUUID();
        UUID storage = UUID.randomUUID();
        CompoundTag key = new CompoundTag();
        key.putString("type", "test:key");
        ExactStorageMutationJournal.Step step = new ExactStorageMutationJournal.Step(
                storage,
                key,
                BigInteger.TEN,
                BigInteger.valueOf(7L),
                BigInteger.TEN,
                BigInteger.valueOf(7L),
                1,
                1,
                BigInteger.valueOf(3L));

        assertTrue(journal.begin(operation, 12L, "EXTRACT", List.of(step), 16));
        assertFalse(journal.pending().getFirst().steps().getFirst().applied());
        assertTrue(journal.markApplied(operation, 0));
        assertTrue(journal.pending().getFirst().steps().getFirst().applied());

        CompoundTag saved = journal.save(new CompoundTag(), null);
        ExactStorageMutationJournal loaded =
                ExactStorageMutationJournal.load(saved, null);
        ExactStorageMutationJournal.Entry entry = loaded.pending().getFirst();
        assertEquals(12L, entry.generation());
        assertEquals(operation, entry.operationId());
        assertTrue(entry.steps().getFirst().applied());
        assertEquals(BigInteger.valueOf(7L), entry.steps().getFirst().afterAmount());
    }

    @Test
    void malformedJournalIsLockedAgainstOverwrite() {
        CompoundTag malformed = new CompoundTag();
        malformed.putInt("schema", 999);
        malformed.put("records", new net.minecraft.nbt.ListTag());

        ExactStorageMutationJournal journal =
                ExactStorageMutationJournal.load(malformed, null);
        assertFalse(journal.isHealthy());
        assertFalse(journal.begin(
                UUID.randomUUID(),
                1L,
                "INSERT",
                List.of(new ExactStorageMutationJournal.Step(
                        UUID.randomUUID(),
                        new CompoundTag(),
                        BigInteger.ZERO,
                        BigInteger.ONE,
                        BigInteger.ZERO,
                        BigInteger.ONE,
                        0,
                        1,
                        BigInteger.ONE)),
                16));
    }
}

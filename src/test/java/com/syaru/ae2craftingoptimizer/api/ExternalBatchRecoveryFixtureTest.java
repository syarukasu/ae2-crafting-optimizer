package com.syaru.ae2craftingoptimizer.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchTransactionRecord;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ExternalBatchRecoveryFixtureTest {
    @Test
    void consumesOnlyThePublicRecoveryContract() {
        BatchTransactionRecord record = BatchTransactionRecord.snapshot(
                UUID.randomUUID(),
                ResourceLocation.fromNamespaceAndPath("test", "adapter"),
                ResourceLocation.fromNamespaceAndPath("test", "source"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                new BlockPos(1, 2, 3),
                "pattern",
                1L,
                0L,
                1L,
                1L,
                "PREPARED",
                List.of(),
                List.of(),
                new CompoundTag(),
                new CompoundTag(),
                "");

        assertEquals(record.payloadDigest(), ExternalBatchRecoveryFixture.canonicalDigest(record));
    }
}

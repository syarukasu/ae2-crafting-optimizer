package com.syaru.ae2craftingoptimizer.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReceipt;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class BatchSourceReceiptLedgerTest {
    private static final AEKey TEST_IRON = new TestKey("iron");

    @Test
    void recordsTheExactAmountExtractedSoFar() {
        UUID id = UUID.randomUUID();
        BatchSourceReceiptLedger ledger = new BatchSourceReceiptLedger();
        ledger.stage(new BatchSourceReceipt(id, BatchSourceReceipt.State.STAGED, 8L, "task", 0, 1L));
        ledger.advance(id, BatchSourceReceipt.State.EXTRACTING, 0, 2L);

        ledger.recordExtraction(id, new GenericStack(TEST_IRON, 3L), 3L);
        ledger.recordExtraction(id, new GenericStack(TEST_IRON, 2L), 4L);

        assertEquals(1, ledger.get(id).extractedInputs().size());
        assertEquals(5L, ledger.get(id).extractedInputs().get(0).amount());
        ledger.advance(id, BatchSourceReceipt.State.EXTRACTED, 0, 5L);
        assertEquals(5L, ledger.get(id).extractedInputs().get(0).amount());
    }

    @Test
    void extractionAmountsCannotOverflowOrBeRecordedOutsideTheBarrier() {
        UUID id = UUID.randomUUID();
        BatchSourceReceiptLedger ledger = new BatchSourceReceiptLedger();
        ledger.stage(new BatchSourceReceipt(id, BatchSourceReceipt.State.STAGED, 8L, "task", 0, 1L));
        assertThrows(
                IllegalStateException.class,
                () -> ledger.recordExtraction(id, new GenericStack(TEST_IRON, 1L), 1L));
        ledger.advance(id, BatchSourceReceipt.State.EXTRACTING, 0, 2L);
        ledger.recordExtraction(id, new GenericStack(TEST_IRON, Long.MAX_VALUE), 3L);
        assertThrows(
                ArithmeticException.class,
                () -> ledger.recordExtraction(id, new GenericStack(TEST_IRON, 1L), 4L));
        assertEquals(Long.MAX_VALUE, ledger.get(id).extractedInputs().get(0).amount());
    }

    @Test
    void loadsLegacyReceiptsWithoutInventingExtractionEvidence() {
        UUID id = UUID.randomUUID();
        CompoundTag entry = new CompoundTag();
        entry.putUUID("id", id);
        entry.putString("state", BatchSourceReceipt.State.EXTRACTING.name());
        entry.putLong("executions", 2L);
        entry.putString("task", "legacy");
        entry.putInt("accountedOutputs", 0);
        entry.putLong("updatedTick", 1L);
        ListTag entries = new ListTag();
        entries.add(entry);
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("schema", 2);
        legacy.put("entries", entries);

        BatchSourceReceiptLedger restored = new BatchSourceReceiptLedger();
        restored.load(legacy);

        assertTrue(restored.isHealthy());
        assertTrue(restored.get(id).extractedInputs().isEmpty());
    }

    @Test
    void malformedCurrentExtractionEvidenceFailsClosed() {
        UUID id = UUID.randomUUID();
        CompoundTag entry = new CompoundTag();
        entry.putUUID("id", id);
        entry.putString("state", BatchSourceReceipt.State.EXTRACTING.name());
        entry.putLong("executions", 2L);
        entry.putString("task", "malformed");
        entry.putInt("accountedOutputs", 0);
        entry.putLong("updatedTick", 1L);
        ListTag entries = new ListTag();
        entries.add(entry);
        CompoundTag malformed = new CompoundTag();
        malformed.putInt("schema", 3);
        malformed.put("entries", entries);

        BatchSourceReceiptLedger restored = new BatchSourceReceiptLedger();
        restored.load(malformed);

        assertFalse(restored.isHealthy());
        assertEquals(3, restored.save().getInt("schema"));
    }

    @Test
    void persistsForwardOnlySourceState() {
        UUID id = UUID.randomUUID();
        BatchSourceReceiptLedger ledger = new BatchSourceReceiptLedger();
        ledger.stage(new BatchSourceReceipt(id, BatchSourceReceipt.State.STAGED, 8L, "task", 0, 1L));
        ledger.advance(id, BatchSourceReceipt.State.EXTRACTING, 0, 2L);
        ledger.advance(id, BatchSourceReceipt.State.EXTRACTED, 0, 3L);
        ledger.advance(id, BatchSourceReceipt.State.TARGET_ACCEPTED, 0, 4L);
        ledger.advance(id, BatchSourceReceipt.State.ENERGY_ACCOUNTING, 0, 5L);
        ledger.advance(id, BatchSourceReceipt.State.ENERGY_ACCOUNTED, 0, 6L);
        ledger.advance(id, BatchSourceReceipt.State.PROGRESS_ACCOUNTED, 0, 7L);
        ledger.advance(id, BatchSourceReceipt.State.OUTPUTS_ACCOUNTING, 0, 8L);
        ledger.advance(id, BatchSourceReceipt.State.OUTPUT_ACCOUNTING, 0, 9L);
        ledger.advance(id, BatchSourceReceipt.State.OUTPUTS_ACCOUNTING, 1, 10L);
        ledger.advance(id, BatchSourceReceipt.State.ACCOUNTED, 1, 11L);

        BatchSourceReceiptLedger restored = new BatchSourceReceiptLedger();
        restored.load(ledger.save());
        assertEquals(BatchSourceReceipt.State.ACCOUNTED, restored.get(id).state());
        assertThrows(
                IllegalStateException.class,
                () -> restored.advance(id, BatchSourceReceipt.State.ROLLED_BACK, 0, 9L));
    }

    @Test
    void rejectsInvalidOutputCursorMovement() {
        UUID id = UUID.randomUUID();
        BatchSourceReceiptLedger ledger = new BatchSourceReceiptLedger();
        ledger.stage(new BatchSourceReceipt(id, BatchSourceReceipt.State.STAGED, 2L, "task", 0, 1L));
        ledger.advance(id, BatchSourceReceipt.State.EXTRACTING, 0, 2L);
        ledger.advance(id, BatchSourceReceipt.State.EXTRACTED, 0, 3L);
        ledger.advance(id, BatchSourceReceipt.State.TARGET_ACCEPTED, 0, 4L);
        ledger.advance(id, BatchSourceReceipt.State.ENERGY_ACCOUNTING, 0, 5L);
        ledger.advance(id, BatchSourceReceipt.State.ENERGY_ACCOUNTED, 0, 6L);
        ledger.advance(id, BatchSourceReceipt.State.PROGRESS_ACCOUNTED, 0, 7L);
        ledger.advance(id, BatchSourceReceipt.State.OUTPUTS_ACCOUNTING, 0, 8L);

        assertThrows(
                IllegalStateException.class,
                () -> ledger.advance(id, BatchSourceReceipt.State.OUTPUT_ACCOUNTING, 1, 8L));
        ledger.advance(id, BatchSourceReceipt.State.OUTPUT_ACCOUNTING, 0, 9L);
        assertThrows(
                IllegalStateException.class,
                () -> ledger.advance(id, BatchSourceReceipt.State.OUTPUTS_ACCOUNTING, 2, 10L));
        ledger.advance(id, BatchSourceReceipt.State.OUTPUTS_ACCOUNTING, 1, 10L);
        assertThrows(
                IllegalStateException.class,
                () -> ledger.advance(id, BatchSourceReceipt.State.ACCOUNTED, 2, 11L));
    }

    @Test
    void requiresAnExplicitEnergyAccountingBarrier() {
        UUID id = UUID.randomUUID();
        BatchSourceReceiptLedger ledger = new BatchSourceReceiptLedger();
        ledger.stage(new BatchSourceReceipt(id, BatchSourceReceipt.State.STAGED, 2L, "task", 0, 1L));
        ledger.advance(id, BatchSourceReceipt.State.EXTRACTING, 0, 2L);
        ledger.advance(id, BatchSourceReceipt.State.EXTRACTED, 0, 3L);
        ledger.advance(id, BatchSourceReceipt.State.TARGET_ACCEPTED, 0, 4L);

        assertThrows(
                IllegalStateException.class,
                () -> ledger.advance(id, BatchSourceReceipt.State.ENERGY_ACCOUNTED, 0, 5L));
        ledger.advance(id, BatchSourceReceipt.State.ENERGY_ACCOUNTING, 0, 5L);
        assertEquals(BatchSourceReceipt.State.ENERGY_ACCOUNTING, ledger.get(id).state());
    }

    @Test
    void extractionAndOutputSideEffectsHaveUncertaintyBarriers() {
        UUID extractionId = UUID.randomUUID();
        BatchSourceReceiptLedger ledger = new BatchSourceReceiptLedger();
        ledger.stage(new BatchSourceReceipt(
                extractionId, BatchSourceReceipt.State.STAGED, 2L, "extract", 0, 1L));
        ledger.advance(extractionId, BatchSourceReceipt.State.EXTRACTING, 0, 2L);
        assertTrue(ledger.hasUnresolved());
        assertThrows(
                IllegalStateException.class,
                () -> ledger.advance(extractionId, BatchSourceReceipt.State.TARGET_ACCEPTED, 0, 3L));

        UUID outputId = UUID.randomUUID();
        ledger.stage(new BatchSourceReceipt(
                outputId, BatchSourceReceipt.State.STAGED, 2L, "output", 0, 1L));
        ledger.advance(outputId, BatchSourceReceipt.State.EXTRACTING, 0, 2L);
        ledger.advance(outputId, BatchSourceReceipt.State.EXTRACTED, 0, 3L);
        ledger.advance(outputId, BatchSourceReceipt.State.TARGET_ACCEPTED, 0, 4L);
        ledger.advance(outputId, BatchSourceReceipt.State.ENERGY_ACCOUNTING, 0, 5L);
        ledger.advance(outputId, BatchSourceReceipt.State.ENERGY_ACCOUNTED, 0, 6L);
        ledger.advance(outputId, BatchSourceReceipt.State.PROGRESS_ACCOUNTED, 0, 7L);
        ledger.advance(outputId, BatchSourceReceipt.State.OUTPUTS_ACCOUNTING, 0, 8L);
        ledger.advance(outputId, BatchSourceReceipt.State.OUTPUT_ACCOUNTING, 0, 9L);
        assertTrue(ledger.hasUnresolved());
        assertThrows(
                IllegalStateException.class,
                () -> ledger.advance(outputId, BatchSourceReceipt.State.ACCOUNTED, 0, 10L));
    }

    @Test
    void onlyStagesFreshReceipts() {
        BatchSourceReceiptLedger ledger = new BatchSourceReceiptLedger();
        assertThrows(
                IllegalArgumentException.class,
                () -> ledger.stage(new BatchSourceReceipt(
                        UUID.randomUUID(),
                        BatchSourceReceipt.State.EXTRACTED,
                        2L,
                        "task",
                        0,
                        1L)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BatchSourceReceipt(
                        UUID.randomUUID(),
                        BatchSourceReceipt.State.PROGRESS_ACCOUNTED,
                        2L,
                        "task",
                        1,
                        1L));
    }

    @Test
    void unresolvedSourceStatePausesUntilItBecomesTerminal() {
        UUID id = UUID.randomUUID();
        BatchSourceReceiptLedger ledger = new BatchSourceReceiptLedger();
        assertTrue(ledger.stage(new BatchSourceReceipt(
                id, BatchSourceReceipt.State.STAGED, 2L, "task", 0, 1L)));
        assertTrue(ledger.hasUnresolved());

        ledger.advance(id, BatchSourceReceipt.State.ROLLED_BACK, 0, 2L);

        assertFalse(ledger.hasUnresolved());
    }

    @Test
    void removesOnlyTerminalSourceReceiptAfterJournalCompletion() {
        UUID id = UUID.randomUUID();
        BatchSourceReceiptLedger ledger = new BatchSourceReceiptLedger();
        assertTrue(ledger.stage(new BatchSourceReceipt(
                id, BatchSourceReceipt.State.STAGED, 2L, "task", 0, 1L)));

        assertFalse(ledger.removeTerminal(id));
        ledger.advance(id, BatchSourceReceipt.State.ROLLED_BACK, 0, 2L);
        assertTrue(ledger.removeTerminal(id));
        assertEquals(null, ledger.get(id));
        assertFalse(ledger.removeTerminal(id));
    }

    @Test
    void retainsRecentTerminalSourceEvidence() {
        BatchSourceReceiptLedger ledger = new BatchSourceReceiptLedger();
        for (int index = 0; index < 256; index++) {
            UUID id = UUID.randomUUID();
            assertTrue(ledger.stage(new BatchSourceReceipt(
                    id, BatchSourceReceipt.State.STAGED, 1L, "task-" + index, 0, 0L)));
            ledger.advance(id, BatchSourceReceipt.State.ROLLED_BACK, 0, 1L);
        }

        assertFalse(ledger.stage(new BatchSourceReceipt(
                UUID.randomUUID(), BatchSourceReceipt.State.STAGED, 1L, "too-soon", 0, 2L)));
        assertTrue(ledger.stage(new BatchSourceReceipt(
                UUID.randomUUID(), BatchSourceReceipt.State.STAGED, 1L, "after-retention", 0, 12_001L)));
    }

    @Test
    void unknownSourceSchemaFailsClosedWithoutOverwritingPayload() {
        CompoundTag saved = new CompoundTag();
        saved.putInt("schema", 99);
        saved.putString("futureData", "keep-me");

        BatchSourceReceiptLedger ledger = new BatchSourceReceiptLedger();
        ledger.load(saved);

        assertFalse(ledger.isHealthy());
        assertFalse(ledger.isEmpty());
        assertFalse(ledger.stage(new BatchSourceReceipt(
                UUID.randomUUID(), BatchSourceReceipt.State.STAGED, 1L, "new", 0, 1L)));
        assertEquals("keep-me", ledger.save().getString("futureData"));
    }

    private static final class TestKey extends AEKey {
        private final ResourceLocation id;

        private TestKey(String path) {
            this.id = ResourceLocation.fromNamespaceAndPath("aco_test", path);
        }

        @Override
        public AEKeyType getType() {
            return null;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag() {
            return new CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return id;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public void writeToPacket(FriendlyByteBuf buffer) {
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(id.toString());
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        }
    }
}

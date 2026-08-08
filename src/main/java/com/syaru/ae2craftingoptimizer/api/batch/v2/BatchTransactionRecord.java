package com.syaru.ae2craftingoptimizer.api.batch.v2;

import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Read-only public view of one durable native-batch transaction.
 *
 * <p>The persisted implementation remains private to ACO. Optional adapters
 * use this view during recovery instead of importing ACO's transaction package.
 */
public final class BatchTransactionRecord {
    private final UUID id;
    private final ResourceLocation adapterId;
    private final ResourceLocation sourceId;
    private final ResourceLocation dimensionId;
    private final BlockPos targetPos;
    private final String patternFingerprint;
    private final long offeredExecutions;
    private final long acceptedExecutions;
    private final long createdTick;
    private final long updatedTick;
    private final String phase;
    private final List<GenericStack> extractedInputs;
    private final List<GenericStack> expectedOutputs;
    private final CompoundTag sourceData;
    private final CompoundTag adapterData;
    private final String receipt;

    BatchTransactionRecord(
            UUID id,
            ResourceLocation adapterId,
            ResourceLocation sourceId,
            ResourceLocation dimensionId,
            BlockPos targetPos,
            String patternFingerprint,
            long offeredExecutions,
            long acceptedExecutions,
            long createdTick,
            long updatedTick,
            String phase,
            List<GenericStack> extractedInputs,
            List<GenericStack> expectedOutputs,
            CompoundTag sourceData,
            CompoundTag adapterData,
            String receipt) {
        this.id = Objects.requireNonNull(id, "id");
        this.adapterId = Objects.requireNonNull(adapterId, "adapterId");
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        this.targetPos = Objects.requireNonNull(targetPos, "targetPos").immutable();
        this.patternFingerprint = Objects.requireNonNull(patternFingerprint, "patternFingerprint");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.extractedInputs = List.copyOf(Objects.requireNonNull(extractedInputs, "extractedInputs"));
        this.expectedOutputs = List.copyOf(Objects.requireNonNull(expectedOutputs, "expectedOutputs"));
        this.sourceData = Objects.requireNonNull(sourceData, "sourceData").copy();
        this.adapterData = Objects.requireNonNull(adapterData, "adapterData").copy();
        this.receipt = Objects.requireNonNull(receipt, "receipt");
        this.offeredExecutions = offeredExecutions;
        this.acceptedExecutions = acceptedExecutions;
        this.createdTick = createdTick;
        this.updatedTick = updatedTick;
    }

    static BatchTransactionRecord fromInternal(
            com.syaru.ae2craftingoptimizer.transaction.BatchTransactionRecord record) {
        return new BatchTransactionRecord(
                record.id(),
                record.adapterId(),
                record.sourceId(),
                record.dimensionId(),
                record.targetPos(),
                record.patternFingerprint(),
                record.offeredExecutions(),
                record.acceptedExecutions(),
                record.createdTick(),
                record.updatedTick(),
                record.phase().name(),
                record.extractedInputs(),
                record.expectedOutputs(),
                record.sourceData(),
                record.adapterData(),
                record.receipt());
    }

    public UUID id() { return id; }
    public ResourceLocation adapterId() { return adapterId; }
    public ResourceLocation sourceId() { return sourceId; }
    public ResourceLocation dimensionId() { return dimensionId; }
    public BlockPos targetPos() { return targetPos; }
    public String patternFingerprint() { return patternFingerprint; }
    public long offeredExecutions() { return offeredExecutions; }
    public long acceptedExecutions() { return acceptedExecutions; }
    public long createdTick() { return createdTick; }
    public long updatedTick() { return updatedTick; }
    public String phase() { return phase; }
    public List<GenericStack> extractedInputs() { return extractedInputs; }
    public List<GenericStack> expectedOutputs() { return expectedOutputs; }
    public CompoundTag sourceData() { return sourceData.copy(); }
    public CompoundTag adapterData() { return adapterData.copy(); }
    public String receipt() { return receipt; }
}

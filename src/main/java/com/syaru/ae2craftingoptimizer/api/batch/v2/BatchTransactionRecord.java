package com.syaru.ae2craftingoptimizer.api.batch.v2;

import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Native Batchの永続Journalを外部Adapterへ公開する読み取り専用Snapshot。
 *
 * <p>ACO内部の可変Recordは公開せず、復旧判定に必要な値だけを防御コピーで渡す。</p>
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

    private BatchTransactionRecord(
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

    /** ACOが永続化した入力・出力・実行数から正規の所有権Digestを返す。 */
    public String payloadDigest() {
        return BatchPayloadFingerprint.of(this);
    }

    /** ACO内部Journalから不変の公開Snapshotを作る。 */
    public static BatchTransactionRecord snapshot(
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
        return new BatchTransactionRecord(
                id,
                adapterId,
                sourceId,
                dimensionId,
                targetPos,
                patternFingerprint,
                offeredExecutions,
                acceptedExecutions,
                createdTick,
                updatedTick,
                phase,
                extractedInputs,
                expectedOutputs,
                sourceData,
                adapterData,
                receipt);
    }
}

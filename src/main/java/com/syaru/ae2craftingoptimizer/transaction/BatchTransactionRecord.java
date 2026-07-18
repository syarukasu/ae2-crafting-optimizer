package com.syaru.ae2craftingoptimizer.transaction;

import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Native Batch一件の不変入力、期待出力、現在Phase、送受信Receiptを保持する永続Record。
 * 更新時は新しいRecordへ置き換え、過去Phaseへ戻る遷移を許可しない。
 */
public final class BatchTransactionRecord {
    public static final int SCHEMA_VERSION = 1;
    private static final int MAX_STACK_ENTRIES = 16_384;

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
    private final BatchTransactionPhase phase;
    private final List<GenericStack> extractedInputs;
    private final List<GenericStack> expectedOutputs;
    private final CompoundTag sourceData;
    private final CompoundTag adapterData;
    private final String receipt;

    public BatchTransactionRecord(
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
            BatchTransactionPhase phase,
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
        this.patternFingerprint = requireText(patternFingerprint, "patternFingerprint", 4096);
        if (offeredExecutions <= 0L) {
            throw new IllegalArgumentException("offeredExecutions must be positive");
        }
        if (acceptedExecutions < 0L || acceptedExecutions > offeredExecutions) {
            throw new IllegalArgumentException("acceptedExecutions must be between zero and offeredExecutions");
        }
        if ((phase == BatchTransactionPhase.TARGET_ACCEPTED || phase == BatchTransactionPhase.ACCOUNTED)
                && acceptedExecutions == 0L) {
            throw new IllegalArgumentException("accepted transaction phases require a positive accepted count");
        }
        if ((phase == BatchTransactionPhase.PREPARED || phase == BatchTransactionPhase.ROLLED_BACK)
                && acceptedExecutions != 0L) {
            throw new IllegalArgumentException("unaccepted transaction phases require a zero accepted count");
        }
        if (createdTick < 0L || updatedTick < createdTick) {
            throw new IllegalArgumentException("invalid transaction ticks");
        }
        this.offeredExecutions = offeredExecutions;
        this.acceptedExecutions = acceptedExecutions;
        this.createdTick = createdTick;
        this.updatedTick = updatedTick;
        this.phase = Objects.requireNonNull(phase, "phase");
        this.extractedInputs = copyStacks(extractedInputs, "extractedInputs");
        this.expectedOutputs = copyStacks(expectedOutputs, "expectedOutputs");
        this.sourceData = Objects.requireNonNull(sourceData, "sourceData").copy();
        this.adapterData = Objects.requireNonNull(adapterData, "adapterData").copy();
        this.receipt = requireTextAllowEmpty(receipt, "receipt", 16_384);
    }

    public UUID id() {
        return id;
    }

    public ResourceLocation adapterId() {
        return adapterId;
    }

    public ResourceLocation sourceId() {
        return sourceId;
    }

    public ResourceLocation dimensionId() {
        return dimensionId;
    }

    public BlockPos targetPos() {
        return targetPos;
    }

    public String patternFingerprint() {
        return patternFingerprint;
    }

    public long offeredExecutions() {
        return offeredExecutions;
    }

    public long acceptedExecutions() {
        return acceptedExecutions;
    }

    public long createdTick() {
        return createdTick;
    }

    public long updatedTick() {
        return updatedTick;
    }

    public BatchTransactionPhase phase() {
        return phase;
    }

    public List<GenericStack> extractedInputs() {
        return extractedInputs;
    }

    public List<GenericStack> expectedOutputs() {
        return expectedOutputs;
    }

    public CompoundTag sourceData() {
        return sourceData.copy();
    }

    public CompoundTag adapterData() {
        return adapterData.copy();
    }

    public String receipt() {
        return receipt;
    }

    public BatchTransactionRecord transition(
            BatchTransactionPhase next,
            long accepted,
            long tick,
            CompoundTag nextAdapterData,
            String nextReceipt) {
        Objects.requireNonNull(next, "next");
        if (!phase.canTransitionTo(next)) {
            throw new IllegalStateException("invalid transaction transition " + phase + " -> " + next);
        }
        if (next == phase && accepted != acceptedExecutions) {
            throw new IllegalStateException("idempotent transaction transition changed accepted executions");
        }
        return new BatchTransactionRecord(
                id,
                adapterId,
                sourceId,
                dimensionId,
                targetPos,
                patternFingerprint,
                offeredExecutions,
                accepted,
                createdTick,
                Math.max(updatedTick, tick),
                next,
                extractedInputs,
                expectedOutputs,
                sourceData,
                nextAdapterData,
                nextReceipt);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA_VERSION);
        tag.putUUID("id", id);
        tag.putString("adapter", adapterId.toString());
        tag.putString("source", sourceId.toString());
        tag.putString("dimension", dimensionId.toString());
        tag.putLong("target", targetPos.asLong());
        tag.putString("pattern", patternFingerprint);
        tag.putLong("offered", offeredExecutions);
        tag.putLong("accepted", acceptedExecutions);
        tag.putLong("createdTick", createdTick);
        tag.putLong("updatedTick", updatedTick);
        tag.putString("phase", phase.name());
        tag.put("inputs", writeStacks(extractedInputs));
        tag.put("outputs", writeStacks(expectedOutputs));
        tag.put("sourceData", sourceData.copy());
        tag.put("adapterData", adapterData.copy());
        tag.putString("receipt", receipt);
        return tag;
    }

    public static BatchTransactionRecord load(CompoundTag tag) {
        if (tag.getInt("schema") != SCHEMA_VERSION || !tag.hasUUID("id")) {
            throw new IllegalArgumentException("unsupported or malformed batch transaction schema");
        }
        ListTag inputs = requireCompoundList(tag, "inputs");
        ListTag outputs = requireCompoundList(tag, "outputs");
        CompoundTag sourceData = requireCompound(tag, "sourceData");
        CompoundTag adapterData = requireCompound(tag, "adapterData");
        return new BatchTransactionRecord(
                tag.getUUID("id"),
                parseId(tag.getString("adapter"), "adapter"),
                parseId(tag.getString("source"), "source"),
                parseId(tag.getString("dimension"), "dimension"),
                BlockPos.of(tag.getLong("target")),
                tag.getString("pattern"),
                tag.getLong("offered"),
                tag.getLong("accepted"),
                tag.getLong("createdTick"),
                tag.getLong("updatedTick"),
                parsePhase(tag.getString("phase")),
                readStacks(inputs),
                readStacks(outputs),
                sourceData,
                adapterData,
                tag.getString("receipt"));
    }

    private static List<GenericStack> copyStacks(List<GenericStack> stacks, String name) {
        Objects.requireNonNull(stacks, name);
        if (stacks.size() > MAX_STACK_ENTRIES) {
            throw new IllegalArgumentException(name + " exceeds " + MAX_STACK_ENTRIES + " entries");
        }
        List<GenericStack> copy = new ArrayList<>(stacks.size());
        for (GenericStack stack : stacks) {
            Objects.requireNonNull(stack, name + " entry");
            if (stack.amount() <= 0L) {
                throw new IllegalArgumentException(name + " amounts must be positive");
            }
            copy.add(stack);
        }
        return List.copyOf(copy);
    }

    private static ListTag writeStacks(List<GenericStack> stacks) {
        ListTag list = new ListTag();
        for (GenericStack stack : stacks) {
            list.add(GenericStack.writeTag(stack));
        }
        return list;
    }

    private static List<GenericStack> readStacks(ListTag list) {
        if (list.size() > MAX_STACK_ENTRIES) {
            throw new IllegalArgumentException("saved stack list exceeds hard cap");
        }
        List<GenericStack> result = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            GenericStack stack = GenericStack.readTag(list.getCompound(index));
            if (stack == null) {
                throw new IllegalArgumentException("invalid GenericStack at index " + index);
            }
            result.add(stack);
        }
        return result;
    }

    private static ResourceLocation parseId(String value, String name) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("invalid " + name + " id " + value);
        }
        return id;
    }

    private static BatchTransactionPhase parsePhase(String value) {
        try {
            return BatchTransactionPhase.valueOf(value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid batch transaction phase " + value, failure);
        }
    }

    private static ListTag requireCompoundList(CompoundTag owner, String name) {
        Tag raw = owner.get(name);
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("missing or malformed batch transaction list " + name);
        }
        return list;
    }

    private static CompoundTag requireCompound(CompoundTag owner, String name) {
        Tag raw = owner.get(name);
        if (!(raw instanceof CompoundTag compound)) {
            throw new IllegalArgumentException("missing or malformed batch transaction compound " + name);
        }
        return compound;
    }

    private static String requireText(String value, String name, int maximumLength) {
        String checked = Objects.requireNonNull(value, name);
        if (checked.isEmpty() || checked.length() > maximumLength) {
            throw new IllegalArgumentException(name + " length must be between 1 and " + maximumLength);
        }
        return checked;
    }

    private static String requireTextAllowEmpty(String value, String name, int maximumLength) {
        String checked = Objects.requireNonNull(value, name);
        if (checked.length() > maximumLength) {
            throw new IllegalArgumentException(name + " length exceeds " + maximumLength);
        }
        return checked;
    }
}

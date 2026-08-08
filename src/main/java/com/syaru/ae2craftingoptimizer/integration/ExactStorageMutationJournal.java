package com.syaru.ae2craftingoptimizer.integration;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Exact BigInteger storage mutation proof.
 *
 * <p>The record is written before a cell is changed. Each route step then records
 * its applied state. Recovery can therefore distinguish an already-applied step
 * from an untouched step without comparing an unrelated network-wide total.</p>
 */
public final class ExactStorageMutationJournal extends SavedData {
    public static final String DATA_NAME = "ae2_crafting_optimizer_exact_storage_mutations";
    private static final int SCHEMA_VERSION = 1;
    private static final int HARD_MAXIMUM_RECORDS = 16_384;
    private static final int HARD_MAXIMUM_STEPS = 65_536;

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private CompoundTag unsupportedPayload;

    public static ExactStorageMutationJournal forGrid(appeng.api.networking.IGrid grid) {
        Objects.requireNonNull(grid, "grid");
        appeng.api.networking.IGridNode pivot = grid.getPivot();
        if (pivot == null || pivot.getLevel() == null) {
            return null;
        }
        return pivot.getLevel().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        ExactStorageMutationJournal::new,
                        ExactStorageMutationJournal::load),
                DATA_NAME);
    }

    public synchronized boolean begin(
            UUID operationId,
            long generation,
            String direction,
            List<Step> steps,
            int maximumEntries) {
        if (!isHealthy() || entries.containsKey(operationId)) {
            return false;
        }
        if (entries.size() >= Math.min(maximumEntries, HARD_MAXIMUM_RECORDS)
                || steps.isEmpty()
                || steps.size() > HARD_MAXIMUM_STEPS) {
            return false;
        }
        entries.put(
                operationId,
                new Entry(
                        operationId,
                        generation,
                        requireDirection(direction),
                        steps));
        setDirty();
        return true;
    }

    public synchronized List<Entry> pending() {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (!entry.quarantined()) {
                result.add(entry.copy());
            }
        }
        return List.copyOf(result);
    }

    public synchronized boolean markApplied(UUID operationId, int stepIndex) {
        Entry entry = requireEntry(operationId);
        if (stepIndex < 0 || stepIndex >= entry.steps().size()) {
            return false;
        }
        Step step = entry.stepAt(stepIndex);
        if (step.applied()) {
            return true;
        }
        entry.replaceStep(stepIndex, step.appliedCopy());
        setDirty();
        return true;
    }

    public synchronized boolean acknowledge(UUID operationId) {
        if (entries.remove(operationId) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public synchronized boolean quarantine(UUID operationId, String reason) {
        Entry entry = entries.get(operationId);
        if (entry == null) {
            return false;
        }
        entry.quarantined = true;
        entry.quarantineReason = Objects.requireNonNull(reason, "reason");
        setDirty();
        return true;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized boolean isHealthy() {
        return unsupportedPayload == null;
    }

    @Override
    public synchronized CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries) {
        if (unsupportedPayload != null) {
            return unsupportedPayload.copy();
        }
        tag.putInt("schema", SCHEMA_VERSION);
        ListTag records = new ListTag();
        for (Entry entry : entries.values()) {
            records.add(entry.save());
        }
        tag.put("records", records);
        return tag;
    }

    public static ExactStorageMutationJournal load(
            CompoundTag tag,
            HolderLookup.Provider registries) {
        ExactStorageMutationJournal journal = new ExactStorageMutationJournal();
        if (tag.getInt("schema") != SCHEMA_VERSION
                || !tag.contains("records", Tag.TAG_LIST)) {
            journal.unsupportedPayload = tag.copy();
            AE2CraftingOptimizer.LOGGER.error(
                    "ACO locked an unsupported exact storage journal schema against overwrite");
            return journal;
        }
        ListTag records = tag.getList("records", Tag.TAG_COMPOUND);
        if (records.size() > HARD_MAXIMUM_RECORDS) {
            journal.unsupportedPayload = tag.copy();
            AE2CraftingOptimizer.LOGGER.error(
                    "ACO locked an oversized exact storage journal against overwrite: {} records",
                    records.size());
            return journal;
        }
        for (int index = 0; index < records.size(); index++) {
            try {
                Entry entry = Entry.load(records.getCompound(index));
                if (journal.entries.putIfAbsent(entry.operationId(), entry) != null) {
                    throw new IllegalArgumentException("duplicate exact operation id");
                }
            } catch (RuntimeException failure) {
                journal.unsupportedPayload = tag.copy();
                AE2CraftingOptimizer.LOGGER.error(
                        "ACO locked a malformed exact storage journal against overwrite at record {}",
                        index,
                        failure);
                return journal;
            }
        }
        return journal;
    }

    private Entry requireEntry(UUID operationId) {
        Entry entry = entries.get(operationId);
        if (entry == null) {
            throw new IllegalStateException(
                    "unknown exact storage operation " + operationId);
        }
        return entry;
    }

    private static String requireDirection(String direction) {
        if (!"INSERT".equals(direction) && !"EXTRACT".equals(direction)) {
            throw new IllegalArgumentException("invalid exact storage direction");
        }
        return direction;
    }

    public static final class Entry {
        private final UUID operationId;
        private final long generation;
        private final String direction;
        private final List<Step> steps;
        private boolean quarantined;
        private String quarantineReason = "";

        private Entry(
                UUID operationId,
                long generation,
                String direction,
                List<Step> steps) {
            this.operationId = Objects.requireNonNull(operationId, "operationId");
            if (generation < 0L) {
                throw new IllegalArgumentException("generation must not be negative");
            }
            this.generation = generation;
            this.direction = requireDirection(direction);
            if (steps.isEmpty() || steps.size() > HARD_MAXIMUM_STEPS) {
                throw new IllegalArgumentException("invalid exact journal step count");
            }
            this.steps = new ArrayList<>();
            for (Step step : steps) {
                this.steps.add(Objects.requireNonNull(step, "journal step"));
            }
        }

        public UUID operationId() {
            return operationId;
        }

        public long generation() {
            return generation;
        }

        public String direction() {
            return direction;
        }

        public List<Step> steps() {
            return List.copyOf(steps);
        }

        private Step stepAt(int index) {
            return steps.get(index);
        }

        private void replaceStep(int index, Step replacement) {
            steps.set(index, replacement);
        }

        public boolean quarantined() {
            return quarantined;
        }

        public String quarantineReason() {
            return quarantineReason;
        }

        private Entry copy() {
            Entry result = new Entry(operationId, generation, direction, steps);
            result.quarantined = quarantined;
            result.quarantineReason = quarantineReason;
            return result;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", operationId);
            tag.putLong("generation", generation);
            tag.putString("direction", direction);
            tag.putBoolean("quarantined", quarantined);
            tag.putString("quarantineReason", quarantineReason);
            ListTag savedSteps = new ListTag();
            for (Step step : steps) {
                savedSteps.add(step.save());
            }
            tag.put("steps", savedSteps);
            return tag;
        }

        private static Entry load(CompoundTag tag) {
            if (!tag.hasUUID("id")
                    || !tag.contains("steps", Tag.TAG_LIST)) {
                throw new IllegalArgumentException("malformed exact journal record");
            }
            ListTag steps = tag.getList("steps", Tag.TAG_COMPOUND);
            if (steps.isEmpty() || steps.size() > HARD_MAXIMUM_STEPS) {
                throw new IllegalArgumentException("invalid exact journal step list");
            }
            List<Step> loaded = new ArrayList<>(steps.size());
            for (int index = 0; index < steps.size(); index++) {
                loaded.add(Step.load(steps.getCompound(index)));
            }
            Entry entry = new Entry(
                    tag.getUUID("id"),
                    tag.getLong("generation"),
                    tag.getString("direction"),
                    loaded);
            entry.quarantined = tag.getBoolean("quarantined");
            entry.quarantineReason = tag.getString("quarantineReason");
            return entry;
        }
    }

    /** A single cell/key boundary proof; the key tag is decoded only during recovery. */
    public static final class Step {
        private final UUID storageId;
        private final CompoundTag key;
        private final BigInteger beforeAmount;
        private final BigInteger afterAmount;
        private final BigInteger beforeTotal;
        private final BigInteger afterTotal;
        private final int beforeTypes;
        private final int afterTypes;
        private final BigInteger amount;
        private final boolean applied;

        public Step(
                UUID storageId,
                CompoundTag key,
                BigInteger beforeAmount,
                BigInteger afterAmount,
                BigInteger beforeTotal,
                BigInteger afterTotal,
                int beforeTypes,
                int afterTypes,
                BigInteger amount) {
            this(
                    storageId,
                    key,
                    beforeAmount,
                    afterAmount,
                    beforeTotal,
                    afterTotal,
                    beforeTypes,
                    afterTypes,
                    amount,
                    false);
        }

        private Step(
                UUID storageId,
                CompoundTag key,
                BigInteger beforeAmount,
                BigInteger afterAmount,
                BigInteger beforeTotal,
                BigInteger afterTotal,
                int beforeTypes,
                int afterTypes,
                BigInteger amount,
                boolean applied) {
            this.storageId = Objects.requireNonNull(storageId, "storageId");
            this.key = Objects.requireNonNull(key, "key").copy();
            this.beforeAmount = requireNonNegative(beforeAmount, "beforeAmount");
            this.afterAmount = requireNonNegative(afterAmount, "afterAmount");
            this.beforeTotal = requireNonNegative(beforeTotal, "beforeTotal");
            this.afterTotal = requireNonNegative(afterTotal, "afterTotal");
            if (beforeTypes < 0 || afterTypes < 0) {
                throw new IllegalArgumentException("type counts must not be negative");
            }
            this.beforeTypes = beforeTypes;
            this.afterTypes = afterTypes;
            this.amount = requirePositive(amount, "amount");
            this.applied = applied;
        }

        public UUID storageId() {
            return storageId;
        }

        public CompoundTag key() {
            return key.copy();
        }

        public BigInteger beforeAmount() {
            return beforeAmount;
        }

        public BigInteger afterAmount() {
            return afterAmount;
        }

        public BigInteger beforeTotal() {
            return beforeTotal;
        }

        public BigInteger afterTotal() {
            return afterTotal;
        }

        public int beforeTypes() {
            return beforeTypes;
        }

        public int afterTypes() {
            return afterTypes;
        }

        public BigInteger amount() {
            return amount;
        }

        public boolean applied() {
            return applied;
        }

        private Step appliedCopy() {
            return new Step(
                    storageId,
                    key,
                    beforeAmount,
                    afterAmount,
                    beforeTotal,
                    afterTotal,
                    beforeTypes,
                    afterTypes,
                    amount,
                    true);
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("storage", storageId);
            tag.put("key", key.copy());
            tag.putString("beforeAmount", beforeAmount.toString());
            tag.putString("afterAmount", afterAmount.toString());
            tag.putString("beforeTotal", beforeTotal.toString());
            tag.putString("afterTotal", afterTotal.toString());
            tag.putInt("beforeTypes", beforeTypes);
            tag.putInt("afterTypes", afterTypes);
            tag.putString("amount", amount.toString());
            tag.putBoolean("applied", applied);
            return tag;
        }

        private static Step load(CompoundTag tag) {
            if (!tag.hasUUID("storage")
                    || !(tag.get("key") instanceof CompoundTag key)) {
                throw new IllegalArgumentException("malformed exact journal step");
            }
            return new Step(
                    tag.getUUID("storage"),
                    key,
                    parseNonNegative(tag, "beforeAmount"),
                    parseNonNegative(tag, "afterAmount"),
                    parseNonNegative(tag, "beforeTotal"),
                    parseNonNegative(tag, "afterTotal"),
                    tag.getInt("beforeTypes"),
                    tag.getInt("afterTypes"),
                    parsePositive(tag, "amount"),
                    tag.getBoolean("applied"));
        }

        private static BigInteger parseNonNegative(CompoundTag tag, String name) {
            return requireNonNegative(parse(tag, name), name);
        }

        private static BigInteger parsePositive(CompoundTag tag, String name) {
            return requirePositive(parse(tag, name), name);
        }

        private static BigInteger parse(CompoundTag tag, String name) {
            try {
                return new BigInteger(tag.getString(name));
            } catch (RuntimeException failure) {
                throw new IllegalArgumentException("invalid exact journal number " + name, failure);
            }
        }

        private static BigInteger requireNonNegative(BigInteger value, String name) {
            BigInteger checked = Objects.requireNonNull(value, name);
            if (checked.signum() < 0) {
                throw new IllegalArgumentException(name + " must not be negative");
            }
            return checked;
        }

        private static BigInteger requirePositive(BigInteger value, String name) {
            BigInteger checked = Objects.requireNonNull(value, name);
            if (checked.signum() <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return checked;
        }
    }
}

package com.syaru.ae2craftingoptimizer.transaction;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

public final class BatchTransactionJournal extends SavedData {
    public static final String DATA_NAME = "ae2_crafting_optimizer_batch_transactions";
    private static final int SCHEMA_VERSION = 1;
    public static final int HARD_MAXIMUM_RECORDS = 16_384;
    private final Map<UUID, BatchTransactionRecord> records = new LinkedHashMap<>();
    private final List<CompoundTag> malformedRecords = new ArrayList<>();
    private CompoundTag unsupportedPayload;

    public synchronized boolean putPrepared(BatchTransactionRecord record, int maximumEntries) {
        if (!isHealthy()) {
            return false;
        }
        if (record.phase() != BatchTransactionPhase.PREPARED) {
            throw new IllegalArgumentException("only PREPARED transactions can be inserted");
        }
        if (records.containsKey(record.id())) {
            throw new IllegalStateException("duplicate transaction id " + record.id());
        }
        int effectiveMaximum = Math.min(maximumEntries, HARD_MAXIMUM_RECORDS);
        if (effectiveMaximum < 1 || size() >= effectiveMaximum) {
            return false;
        }
        records.put(record.id(), record);
        setDirty();
        return true;
    }

    public synchronized BatchTransactionRecord transition(
            UUID id,
            BatchTransactionPhase next,
            long accepted,
            long tick,
            CompoundTag adapterData,
            String receipt) {
        BatchTransactionRecord current = records.get(id);
        if (current == null) {
            throw new IllegalStateException("unknown transaction " + id);
        }
        BatchTransactionRecord updated = current.transition(next, accepted, tick, adapterData, receipt);
        records.put(id, updated);
        setDirty();
        return updated;
    }

    public synchronized BatchTransactionRecord get(UUID id) {
        return records.get(id);
    }

    public synchronized boolean removeTerminal(UUID id) {
        BatchTransactionRecord record = records.get(id);
        if (record == null || !record.phase().terminal()) {
            return false;
        }
        records.remove(id);
        setDirty();
        return true;
    }

    public synchronized List<BatchTransactionRecord> pending(int maximum) {
        List<BatchTransactionRecord> result = new ArrayList<>(Math.min(maximum, records.size()));
        for (BatchTransactionRecord record : records.values()) {
            if (!record.phase().terminal()) {
                result.add(record);
                if (result.size() >= maximum) {
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    public synchronized int size() {
        return records.size() + malformedRecords.size();
    }

    public synchronized boolean isHealthy() {
        return unsupportedPayload == null && malformedRecords.isEmpty();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        if (unsupportedPayload != null) {
            return unsupportedPayload.copy();
        }
        tag.putInt("schema", SCHEMA_VERSION);
        ListTag transactions = new ListTag();
        for (BatchTransactionRecord record : records.values()) {
            transactions.add(record.save());
        }
        tag.put("transactions", transactions);
        ListTag malformed = new ListTag();
        for (CompoundTag record : malformedRecords) {
            malformed.add(record.copy());
        }
        tag.put("malformedTransactions", malformed);
        return tag;
    }

    public static BatchTransactionJournal load(CompoundTag tag) {
        BatchTransactionJournal journal = new BatchTransactionJournal();
        if (tag.getInt("schema") != SCHEMA_VERSION) {
            journal.unsupportedPayload = tag.copy();
            AE2CraftingOptimizer.LOGGER.error(
                    "ACO locked an unsupported batch transaction journal schema against overwrite: {}",
                    tag.getInt("schema"));
            return journal;
        }
        if ((tag.contains("malformedTransactions")
                        && !tag.contains("malformedTransactions", Tag.TAG_LIST))
                || !tag.contains("transactions", Tag.TAG_LIST)) {
            journal.unsupportedPayload = tag.copy();
            AE2CraftingOptimizer.LOGGER.error(
                    "ACO locked a structurally malformed transaction journal against overwrite");
            return journal;
        }
        ListTag previouslyMalformed = tag.getList("malformedTransactions", Tag.TAG_COMPOUND);
        ListTag transactions = tag.getList("transactions", Tag.TAG_COMPOUND);
        Tag rawMalformed = tag.get("malformedTransactions");
        Tag rawTransactions = tag.get("transactions");
        if ((rawMalformed instanceof ListTag rawMalformedList
                        && !rawMalformedList.isEmpty()
                        && rawMalformedList.getElementType() != Tag.TAG_COMPOUND)
                || !(rawTransactions instanceof ListTag rawTransactionList)
                || (!rawTransactionList.isEmpty()
                        && rawTransactionList.getElementType() != Tag.TAG_COMPOUND)) {
            journal.unsupportedPayload = tag.copy();
            AE2CraftingOptimizer.LOGGER.error(
                    "ACO locked a transaction journal with invalid list element types against overwrite");
            return journal;
        }
        if (previouslyMalformed.size() > HARD_MAXIMUM_RECORDS) {
            journal.unsupportedPayload = tag.copy();
            AE2CraftingOptimizer.LOGGER.error(
                    "ACO locked an oversized malformed-transaction quarantine against overwrite: {} entries",
                    previouslyMalformed.size());
            return journal;
        }
        if (transactions.size() > HARD_MAXIMUM_RECORDS) {
            journal.unsupportedPayload = tag.copy();
            AE2CraftingOptimizer.LOGGER.error(
                    "ACO locked an oversized transaction journal against overwrite: {} entries",
                    transactions.size());
            return journal;
        }
        for (int index = 0; index < previouslyMalformed.size(); index++) {
            journal.malformedRecords.add(previouslyMalformed.getCompound(index).copy());
        }
        for (int index = 0; index < transactions.size(); index++) {
            try {
                BatchTransactionRecord record = BatchTransactionRecord.load(transactions.getCompound(index));
                if (!record.phase().terminal()
                        || record.phase() == BatchTransactionPhase.QUARANTINED) {
                    if (journal.records.putIfAbsent(record.id(), record) != null) {
                        throw new IllegalArgumentException("duplicate transaction id " + record.id());
                    }
                }
            } catch (RuntimeException exception) {
                if (journal.malformedRecords.size() >= HARD_MAXIMUM_RECORDS) {
                    journal.records.clear();
                    journal.malformedRecords.clear();
                    journal.unsupportedPayload = tag.copy();
                    AE2CraftingOptimizer.LOGGER.error(
                            "ACO locked the complete transaction journal against overwrite after the malformed-entry quarantine reached {} records",
                            HARD_MAXIMUM_RECORDS);
                    return journal;
                }
                CompoundTag malformed = transactions.getCompound(index).copy();
                malformed.putString("acoQuarantineReason", exception.toString());
                journal.malformedRecords.add(malformed);
                AE2CraftingOptimizer.LOGGER.error(
                        "ACO preserved malformed batch transaction journal entry {} for manual inspection: {}",
                        index,
                        exception.toString());
            }
        }
        return journal;
    }
}

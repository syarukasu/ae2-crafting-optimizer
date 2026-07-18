package com.syaru.ae2craftingoptimizer.batch;

import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReceipt;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class BatchSourceReceiptLedger {
    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_RECEIPTS = 256;
    private static final long TERMINAL_RETENTION_TICKS = 12_000L;
    private final Map<UUID, BatchSourceReceipt> receipts = new LinkedHashMap<>();
    private boolean corrupted;
    private CompoundTag lockedPayload;

    public synchronized boolean isHealthy() {
        return !corrupted;
    }

    public synchronized BatchSourceReceipt get(UUID id) {
        return receipts.get(id);
    }

    public synchronized boolean isEmpty() {
        return receipts.isEmpty() && !corrupted;
    }

    public synchronized boolean hasUnresolved() {
        for (BatchSourceReceipt receipt : receipts.values()) {
            if (receipt.state() != BatchSourceReceipt.State.ACCOUNTED
                    && receipt.state() != BatchSourceReceipt.State.ROLLED_BACK) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean hasUnresolved(String taskFingerprint) {
        for (BatchSourceReceipt receipt : receipts.values()) {
            if (receipt.taskFingerprint().equals(taskFingerprint)
                    && receipt.state() != BatchSourceReceipt.State.ACCOUNTED
                    && receipt.state() != BatchSourceReceipt.State.ROLLED_BACK) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean stage(BatchSourceReceipt receipt) {
        if (corrupted) {
            return false;
        }
        if (receipt.state() != BatchSourceReceipt.State.STAGED || receipt.accountedOutputs() != 0) {
            throw new IllegalArgumentException("new source receipts must begin in STAGED");
        }
        BatchSourceReceipt existing = receipts.get(receipt.transactionId());
        if (existing != null) {
            return existing.executions() == receipt.executions()
                    && existing.taskFingerprint().equals(receipt.taskFingerprint());
        }
        evictExpiredTerminal(receipt.updatedTick());
        if (receipts.size() >= MAX_RECEIPTS) {
            return false;
        }
        receipts.put(receipt.transactionId(), receipt);
        return true;
    }

    public synchronized void advance(
            UUID id,
            BatchSourceReceipt.State next,
            int accountedOutputs,
            long updatedTick) {
        if (corrupted) {
            throw new IllegalStateException("batch source receipt ledger is malformed");
        }
        BatchSourceReceipt current = receipts.get(id);
        if (current == null) {
            throw new IllegalStateException("unknown batch source receipt " + id);
        }
        if (!canTransition(current.state(), next)) {
            throw new IllegalStateException("invalid source receipt transition " + current.state() + " -> " + next);
        }
        if (accountedOutputs < current.accountedOutputs()) {
            throw new IllegalStateException("source receipt output cursor cannot move backwards");
        }
        if (current.state() == BatchSourceReceipt.State.OUTPUTS_ACCOUNTING
                && next == BatchSourceReceipt.State.OUTPUT_ACCOUNTING
                && accountedOutputs != current.accountedOutputs()) {
            throw new IllegalStateException("source receipt output barrier must begin at the current cursor");
        }
        if (current.state() == BatchSourceReceipt.State.OUTPUT_ACCOUNTING
                && next == BatchSourceReceipt.State.OUTPUTS_ACCOUNTING
                && (long) accountedOutputs != (long) current.accountedOutputs() + 1L) {
            throw new IllegalStateException("source receipt output barrier must advance exactly one entry");
        }
        if (next == BatchSourceReceipt.State.ACCOUNTED
                && accountedOutputs != current.accountedOutputs()) {
            throw new IllegalStateException("all output cursor updates must be persisted before accounting completes");
        }
        if (next != BatchSourceReceipt.State.OUTPUT_ACCOUNTING
                && next != BatchSourceReceipt.State.OUTPUTS_ACCOUNTING
                && next != BatchSourceReceipt.State.ACCOUNTED
                && accountedOutputs != 0) {
            throw new IllegalStateException("output cursor is only valid while accounting outputs");
        }
        receipts.put(id, new BatchSourceReceipt(
                id,
                next,
                current.executions(),
                current.taskFingerprint(),
                accountedOutputs,
                Math.max(current.updatedTick(), updatedTick)));
    }

    public synchronized boolean removeTerminal(UUID id) {
        if (corrupted) {
            return false;
        }
        BatchSourceReceipt receipt = receipts.get(id);
        if (receipt == null
                || (receipt.state() != BatchSourceReceipt.State.ACCOUNTED
                        && receipt.state() != BatchSourceReceipt.State.ROLLED_BACK)) {
            return false;
        }
        receipts.remove(id);
        return true;
    }

    public synchronized CompoundTag save() {
        if (lockedPayload != null) {
            return lockedPayload.copy();
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA_VERSION);
        tag.putBoolean("corrupted", corrupted);
        ListTag list = new ListTag();
        for (BatchSourceReceipt receipt : receipts.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", receipt.transactionId());
            entry.putString("state", receipt.state().name());
            entry.putLong("executions", receipt.executions());
            entry.putString("task", receipt.taskFingerprint());
            entry.putInt("accountedOutputs", receipt.accountedOutputs());
            entry.putLong("updatedTick", receipt.updatedTick());
            list.add(entry);
        }
        tag.put("entries", list);
        return tag;
    }

    public synchronized void load(CompoundTag tag) {
        receipts.clear();
        corrupted = false;
        lockedPayload = null;
        if (tag.isEmpty()) {
            return;
        }
        if (tag.getInt("schema") != SCHEMA_VERSION || tag.getBoolean("corrupted")) {
            lock(tag);
            return;
        }
        Tag rawEntries = tag.get("entries");
        if (!(rawEntries instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
                || list.size() > MAX_RECEIPTS) {
            lock(tag);
            return;
        }
        for (int index = 0; index < list.size(); index++) {
            try {
                CompoundTag entry = list.getCompound(index);
                BatchSourceReceipt receipt = new BatchSourceReceipt(
                        entry.getUUID("id"),
                        BatchSourceReceipt.State.valueOf(entry.getString("state")),
                        entry.getLong("executions"),
                        entry.getString("task"),
                        entry.getInt("accountedOutputs"),
                        entry.getLong("updatedTick"));
                if (receipts.putIfAbsent(receipt.transactionId(), receipt) != null) {
                    throw new IllegalArgumentException("duplicate source receipt id " + receipt.transactionId());
                }
            } catch (RuntimeException ignored) {
                lock(tag);
                return;
            }
        }
    }

    private void lock(CompoundTag tag) {
        receipts.clear();
        corrupted = true;
        lockedPayload = tag.copy();
    }

    private static boolean canTransition(BatchSourceReceipt.State current, BatchSourceReceipt.State next) {
        if (current == next) {
            return false;
        }
        return switch (current) {
            case STAGED -> next == BatchSourceReceipt.State.EXTRACTING
                    || next == BatchSourceReceipt.State.ROLLED_BACK;
            case EXTRACTING -> next == BatchSourceReceipt.State.EXTRACTED
                    || next == BatchSourceReceipt.State.ROLLED_BACK;
            case EXTRACTED -> next == BatchSourceReceipt.State.TARGET_ACCEPTED
                    || next == BatchSourceReceipt.State.ROLLED_BACK;
            case TARGET_ACCEPTED -> next == BatchSourceReceipt.State.ENERGY_ACCOUNTING;
            case ENERGY_ACCOUNTING -> next == BatchSourceReceipt.State.ENERGY_ACCOUNTED;
            case ENERGY_ACCOUNTED -> next == BatchSourceReceipt.State.PROGRESS_ACCOUNTED;
            case PROGRESS_ACCOUNTED -> next == BatchSourceReceipt.State.OUTPUTS_ACCOUNTING;
            case OUTPUTS_ACCOUNTING -> next == BatchSourceReceipt.State.OUTPUT_ACCOUNTING
                    || next == BatchSourceReceipt.State.ACCOUNTED;
            case OUTPUT_ACCOUNTING -> next == BatchSourceReceipt.State.OUTPUTS_ACCOUNTING;
            case ACCOUNTED, ROLLED_BACK -> false;
        };
    }

    private void evictExpiredTerminal(long currentTick) {
        Iterator<BatchSourceReceipt> iterator = receipts.values().iterator();
        while (receipts.size() >= MAX_RECEIPTS && iterator.hasNext()) {
            BatchSourceReceipt receipt = iterator.next();
            BatchSourceReceipt.State state = receipt.state();
            if ((state == BatchSourceReceipt.State.ACCOUNTED || state == BatchSourceReceipt.State.ROLLED_BACK)
                    && elapsedAtLeast(currentTick, receipt.updatedTick(), TERMINAL_RETENTION_TICKS)) {
                iterator.remove();
            }
        }
    }

    private static boolean elapsedAtLeast(long now, long then, long duration) {
        return now >= then && now - then >= duration;
    }
}

package com.syaru.ae2craftingoptimizer.batch;

import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceipt;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class NativeBatchReceiptLedger {
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_RECEIPTS = 256;
    private static final long TERMINAL_RETENTION_TICKS = 12_000L;
    private final Map<UUID, NativeBatchReceipt> receipts = new LinkedHashMap<>();
    private boolean corrupted;
    private CompoundTag lockedPayload;

    public synchronized boolean isHealthy() {
        return !corrupted;
    }

    public synchronized NativeBatchReceipt get(UUID id) {
        return receipts.get(id);
    }

    public synchronized boolean isEmpty() {
        return receipts.isEmpty() && !corrupted;
    }

    public synchronized boolean prepare(NativeBatchReceipt receipt) {
        if (corrupted) {
            return false;
        }
        if (receipt.state() != NativeBatchReceipt.State.PENDING) {
            throw new IllegalArgumentException("new native batch receipts must begin in PENDING");
        }
        NativeBatchReceipt existing = receipts.get(receipt.transactionId());
        if (existing != null) {
            return existing.executions() == receipt.executions()
                    && existing.patternFingerprint().equals(receipt.patternFingerprint());
        }
        evictExpiredTerminalReceipts(receipt.updatedTick());
        if (receipts.size() >= MAX_RECEIPTS) {
            return false;
        }
        receipts.put(receipt.transactionId(), receipt);
        return true;
    }

    public synchronized void finish(UUID id, NativeBatchReceipt.State state, long updatedTick) {
        if (corrupted) {
            throw new IllegalStateException("native batch receipt ledger is malformed");
        }
        if (state == NativeBatchReceipt.State.PENDING) {
            throw new IllegalArgumentException("finish state must be terminal");
        }
        NativeBatchReceipt current = receipts.get(id);
        if (current == null) {
            throw new IllegalStateException("unknown native batch receipt " + id);
        }
        if (current.state() != NativeBatchReceipt.State.PENDING && current.state() != state) {
            throw new IllegalStateException("native batch receipt already completed as " + current.state());
        }
        receipts.put(id, new NativeBatchReceipt(
                id,
                state,
                current.executions(),
                current.patternFingerprint(),
                Math.max(current.updatedTick(), updatedTick)));
    }

    public synchronized boolean removeTerminal(UUID id) {
        if (corrupted) {
            return false;
        }
        NativeBatchReceipt receipt = receipts.get(id);
        if (receipt == null || receipt.state() == NativeBatchReceipt.State.PENDING) {
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
        ListTag entries = new ListTag();
        for (NativeBatchReceipt receipt : receipts.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", receipt.transactionId());
            entry.putString("state", receipt.state().name());
            entry.putLong("executions", receipt.executions());
            entry.putString("pattern", receipt.patternFingerprint());
            entry.putLong("updatedTick", receipt.updatedTick());
            entries.add(entry);
        }
        tag.put("entries", entries);
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
        if (!(rawEntries instanceof ListTag entries)
                || (!entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND)
                || entries.size() > MAX_RECEIPTS) {
            lock(tag);
            return;
        }
        for (int index = 0; index < entries.size(); index++) {
            try {
                CompoundTag entry = entries.getCompound(index);
                NativeBatchReceipt receipt = new NativeBatchReceipt(
                        entry.getUUID("id"),
                        NativeBatchReceipt.State.valueOf(entry.getString("state")),
                        entry.getLong("executions"),
                        entry.getString("pattern"),
                        entry.getLong("updatedTick"));
                if (receipts.putIfAbsent(receipt.transactionId(), receipt) != null) {
                    throw new IllegalArgumentException("duplicate native receipt id " + receipt.transactionId());
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

    private void evictExpiredTerminalReceipts(long currentTick) {
        Iterator<NativeBatchReceipt> iterator = receipts.values().iterator();
        while (receipts.size() >= MAX_RECEIPTS && iterator.hasNext()) {
            NativeBatchReceipt receipt = iterator.next();
            if (receipt.state() != NativeBatchReceipt.State.PENDING
                    && elapsedAtLeast(currentTick, receipt.updatedTick(), TERMINAL_RETENTION_TICKS)) {
                iterator.remove();
            }
        }
    }

    private static boolean elapsedAtLeast(long now, long then, long duration) {
        return now >= then && now - then >= duration;
    }
}

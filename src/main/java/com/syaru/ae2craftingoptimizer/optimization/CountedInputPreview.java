package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ICraftingInventory;
import java.util.HashMap;
import java.util.Map;

/** One native ingredient selection without transferring ownership from live stock. */
public final class CountedInputPreview implements ICraftingInventory {
    private final ICraftingInventory source;
    private final Map<AEKey, Long> reserved = new HashMap<>();

    public CountedInputPreview(ICraftingInventory source) { this.source = source; }

    @Override public long extract(AEKey key, long amount, Actionable mode) {
        if (amount < 0) throw new IllegalArgumentException("negative preview extraction");
        long actual = source.extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
        if (actual < 0) throw new IllegalStateException("negative preview stock");
        long used = reserved.getOrDefault(key, 0L);
        long accepted = Math.min(amount, Math.max(0, actual - used));
        if (mode == Actionable.MODULATE && accepted > 0) reserved.put(key, Math.addExact(used, accepted));
        return accepted;
    }

    @Override public void insert(AEKey key, long amount, Actionable mode) {
        if (amount < 0) throw new IllegalArgumentException("negative preview refund");
        if (mode == Actionable.SIMULATE) return;
        long used = reserved.getOrDefault(key, 0L);
        if (amount > used) throw new IllegalStateException("preview refund exceeds reservation");
        if (amount == used) reserved.remove(key);
        else reserved.put(key, used - amount);
    }

    @Override public Iterable<AEKey> findFuzzyTemplates(AEKey key) {
        return source.findFuzzyTemplates(key);
    }
}

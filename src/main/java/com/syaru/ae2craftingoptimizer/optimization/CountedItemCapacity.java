package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** One admission only. Simulation never writes to the real receiver. */
public final class CountedItemCapacity {
    private CountedItemCapacity() {}

    public static long limit(IItemHandler handler, Map<AEKey, BigInteger> inputs, long offered) {
        if (handler == null) return -1;
        if (offered <= 0) return 0;
        int slots = handler.getSlots();
        AEItemKey[] original = new AEItemKey[slots];
        for (int slot = 0; slot < slots; slot++) original[slot] = AEItemKey.of(handler.getStackInSlot(slot));
        AEItemKey[] keys = new AEItemKey[inputs.size()];
        BigInteger[] amounts = new BigInteger[keys.length];
        long[][] space = new long[keys.length][slots];
        int k = 0;
        for (var entry : inputs.entrySet()) {
            if (!(entry.getKey() instanceof AEItemKey key) || entry.getValue().signum() <= 0) return -1;
            keys[k] = key;
            amounts[k] = entry.getValue();
            for (int slot = 0; slot < slots; slot++) {
                if (original[slot] != null && !original[slot].equals(key)) continue;
                ItemStack stored = handler.getStackInSlot(slot);
                long room = Math.max(0L, (long) handler.getSlotLimit(slot) - stored.getCount());
                int request = (int) Math.min(Integer.MAX_VALUE, room);
                if (request == 0) continue;
                ItemStack remainder = handler.insertItem(slot, key.toStack(request), true);
                if (!remainder.isEmpty() && !key.equals(AEItemKey.of(remainder))) return -1;
                long accepted = (long) request - remainder.getCount();
                if (accepted < 0 || accepted > request) return -1;
                space[k][slot] = accepted;
            }
            k++;
        }
        long low = 0, high = offered;
        while (low < high) {
            long middle = low + (high - low) / 2 + 1;
            if (fits(keys, amounts, space, original, middle)) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    private static boolean fits(AEItemKey[] keys, BigInteger[] amounts, long[][] space,
            AEItemKey[] original, long count) {
        AEItemKey[] reserved = original.clone();
        long[] used = new long[original.length];
        for (int k = 0; k < keys.length; k++) {
            BigInteger wanted = amounts[k].multiply(BigInteger.valueOf(count));
            // Match native item insertion: occupied matching slots before empty ones.
            for (int pass = 0; pass < 2; pass++) {
                for (int slot = 0; slot < original.length && wanted.signum() > 0; slot++) {
                    if ((original[slot] == null) != (pass == 1)) continue;
                    if (reserved[slot] != null && !reserved[slot].equals(keys[k])) continue;
                    long room = Math.max(0, space[k][slot] - used[slot]);
                    long take = wanted.min(BigInteger.valueOf(room)).longValueExact();
                    if (take > 0) {
                        reserved[slot] = keys[k];
                        used[slot] += take;
                        wanted = wanted.subtract(BigInteger.valueOf(take));
                    }
                }
            }
            if (wanted.signum() != 0) return false;
        }
        return true;
    }
}

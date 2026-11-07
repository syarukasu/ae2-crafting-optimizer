package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.Arrays;
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
        int[] storedCounts = new int[slots];
        long[] roomBySlot = new long[slots];
        Arrays.fill(roomBySlot, -1L);
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stored = handler.getStackInSlot(slot);
            original[slot] = AEItemKey.of(stored);
            storedCounts[slot] = stored.getCount();
        }
        AEItemKey[] keys = new AEItemKey[inputs.size()];
        long[] amounts = new long[keys.length];
        long[][] space = new long[keys.length][slots];
        long high = offered;
        int k = 0;
        for (var entry : inputs.entrySet()) {
            if (!(entry.getKey() instanceof AEItemKey key) || entry.getValue().signum() <= 0) return -1;
            keys[k] = key;
            BigInteger rawAmount = entry.getValue();
            long perExecution = rawAmount.bitLength() <= 63 ? rawAmount.longValue() : 0L;
            amounts[k] = perExecution;
            long keyTotalSpace = 0L;
            for (int slot = 0; slot < slots; slot++) {
                if (original[slot] != null && !original[slot].equals(key)) continue;
                long room = roomBySlot[slot];
                if (room < 0L) {
                    room = Math.max(0L, (long) handler.getSlotLimit(slot) - storedCounts[slot]);
                    roomBySlot[slot] = room;
                }
                int request = (int) Math.min(Integer.MAX_VALUE, room);
                if (request == 0) continue;
                ItemStack remainder = handler.insertItem(slot, key.toStack(request), true);
                if (!remainder.isEmpty() && !key.equals(AEItemKey.of(remainder))) return -1;
                long accepted = (long) request - remainder.getCount();
                if (accepted < 0 || accepted > request) return -1;
                space[k][slot] = accepted;
                // Both slot count and each simulated acceptance are bounded by int.
                keyTotalSpace += accepted;
            }
            if (perExecution <= 0L) {
                high = 0L;
            } else {
                high = Math.min(high, keyTotalSpace / perExecution);
            }
            k++;
        }
        if (high <= 0L) return 0L;
        AEItemKey[] reserved = new AEItemKey[slots];
        long[] used = new long[slots];
        long low = 0;
        while (low < high) {
            long middle = low + (high - low) / 2 + 1;
            if (fits(keys, amounts, space, original, reserved, used, middle)) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    private static boolean fits(AEItemKey[] keys, long[] amounts, long[][] space,
            AEItemKey[] original, AEItemKey[] reserved, long[] used, long count) {
        System.arraycopy(original, 0, reserved, 0, original.length);
        Arrays.fill(used, 0L);
        for (int k = 0; k < keys.length; k++) {
            // The search bound is no greater than total slot space divided by this amount.
            long wanted = amounts[k] * count;
            // Match native item insertion: occupied matching slots before empty ones.
            for (int pass = 0; pass < 2; pass++) {
                for (int slot = 0; slot < original.length && wanted > 0; slot++) {
                    if ((original[slot] == null) != (pass == 1)) continue;
                    if (reserved[slot] != null && !reserved[slot].equals(keys[k])) continue;
                    long room = Math.max(0, space[k][slot] - used[slot]);
                    long take = Math.min(wanted, room);
                    if (take > 0) {
                        reserved[slot] = keys[k];
                        used[slot] += take;
                        wanted -= take;
                    }
                }
            }
            if (wanted != 0) return false;
        }
        return true;
    }
}

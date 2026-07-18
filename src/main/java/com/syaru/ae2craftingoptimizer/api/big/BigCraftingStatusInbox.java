package com.syaru.ae2craftingoptimizer.api.big;

import appeng.api.stacks.AEKey;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/** Client-side inbox exposed to an integrating status GUI without patching AE2's own packets. */
public final class BigCraftingStatusInbox {
    private static final Map<UUID, BigCraftingStatusPage<AEKey>> PAGES = new ConcurrentHashMap<>();

    private BigCraftingStatusInbox() {
    }

    public static void accept(BigCraftingStatusPage<AEKey> page) {
        PAGES.put(page.runtimeId(), page);
    }

    @Nullable
    public static BigCraftingStatusPage<AEKey> latest(UUID runtimeId) {
        return PAGES.get(runtimeId);
    }

    public static void clear() {
        PAGES.clear();
    }
}

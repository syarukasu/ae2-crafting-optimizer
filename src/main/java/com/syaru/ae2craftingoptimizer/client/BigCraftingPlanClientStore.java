package com.syaru.ae2craftingoptimizer.client;

import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftConfirmMenu;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingPlanSummary;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;

/** 現在開いているCraftConfirmMenuだけへ、BigInteger表示用Sidecarを提供する。 */
public final class BigCraftingPlanClientStore {
    private static volatile Snapshot current;

    private BigCraftingPlanClientStore() {
    }

    public static void accept(
            int containerId,
            BigInteger usedBytes,
            Map<AEKey, BigCraftingPlanSummary.Entry> entries) {
        current = new Snapshot(containerId, usedBytes, entries);
    }

    public static void clear(int containerId) {
        Snapshot snapshot = current;
        if (snapshot != null && snapshot.containerId() == containerId) {
            current = null;
        }
    }

    public static Optional<Snapshot> current() {
        Snapshot snapshot = current;
        var player = Minecraft.getInstance().player;
        if (snapshot == null
                || player == null
                || !(player.containerMenu instanceof CraftConfirmMenu menu)
                || menu.containerId != snapshot.containerId()) {
            return Optional.empty();
        }
        return Optional.of(snapshot);
    }

    public static Optional<BigCraftingPlanSummary.Entry> entry(AEKey key) {
        return current().map(snapshot -> snapshot.entries().get(key));
    }

    public record Snapshot(
            int containerId,
            BigInteger usedBytes,
            Map<AEKey, BigCraftingPlanSummary.Entry> entries) {
        public Snapshot {
            Objects.requireNonNull(usedBytes, "usedBytes");
            entries = Map.copyOf(new LinkedHashMap<>(
                    Objects.requireNonNull(entries, "entries")));
        }
    }
}

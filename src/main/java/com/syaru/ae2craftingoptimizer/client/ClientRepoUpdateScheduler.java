package com.syaru.ae2craftingoptimizer.client;

import appeng.client.gui.me.common.Repo;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(
        modid = AE2CraftingOptimizer.MODID,
        value = Dist.CLIENT)
public final class ClientRepoUpdateScheduler {
    private static final Set<Repo> UPDATED_THIS_TICK =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Repo> PENDING =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean flushing;

    private ClientRepoUpdateScheduler() {
    }

    public static boolean shouldDefer(Repo repo) {
        if (!ACOConfig.coalesceClientTerminalViewUpdates() || flushing) {
            return false;
        }

        if (UPDATED_THIS_TICK.add(repo)) {
            return false;
        }

        PENDING.add(repo);
        return true;
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        UPDATED_THIS_TICK.clear();
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        // このtickでまとめる更新がなければ、Repoへ余計な再計算を要求しない。
        if (PENDING.isEmpty()) {
            return;
        }

        var pending = new ArrayList<>(PENDING);
        PENDING.clear();
        flushing = true;
        try {
            for (Repo repo : pending) {
                repo.updateView();
                UPDATED_THIS_TICK.add(repo);
            }
        } finally {
            flushing = false;
        }
    }
}

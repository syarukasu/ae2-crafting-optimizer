package com.syaru.ae2craftingoptimizer.optimization;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** liveなAE2/Minecraft状態をPlanner用Snapshotへ固定してよいthread境界。 */
public final class ServerPlanningThreadGuard {
    private ServerPlanningThreadGuard() {
    }

    public static boolean canCapture(@Nullable Level level) {
        // serverを持たないLevelやworker threadでは、ACOがlive stateを追加走査しない。
        if (level == null) {
            return false;
        }
        MinecraftServer server = level.getServer();
        return server != null && server.isSameThread();
    }
}

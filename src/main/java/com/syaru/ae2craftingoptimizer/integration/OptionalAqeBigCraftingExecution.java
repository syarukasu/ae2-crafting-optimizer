package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingHostRuntime;
import java.math.BigInteger;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/** Advanced AE未導入環境で対象クラスを解決しないための遅延境界。 */
public final class OptionalAqeBigCraftingExecution {
    private static final String ADVANCED_AE_MODID = "advanced_ae";

    private OptionalAqeBigCraftingExecution() {
    }

    public static void tick(MinecraftServer server) {
        if (ModList.get().isLoaded(ADVANCED_AE_MODID)) {
            AqeBigCraftingExecutionManager.tick(server);
        }
    }

    public static int submit(
            CommandSourceStack source,
            BlockPos position,
            AEKey output,
            BigInteger amount) {
        if (!ModList.get().isLoaded(ADVANCED_AE_MODID)) {
            source.sendFailure(Component.literal("Advanced AE is not installed."));
            return 0;
        }
        return AqeBigCraftingExecutionManager.submitAt(source, position, output, amount);
    }

    public static int cancel(CommandSourceStack source, BlockPos position, UUID jobId) {
        if (!ModList.get().isLoaded(ADVANCED_AE_MODID)) {
            source.sendFailure(Component.literal("Advanced AE is not installed."));
            return 0;
        }
        return AqeBigCraftingExecutionManager.cancelAt(source, position, jobId);
    }

    /**
     * 状態Menuからの取消を、Advanced AEを必須化せず遅延境界越しに処理する。
     */
    public static boolean cancel(
            BigCraftingHostRuntime<AEKey> host,
            UUID jobId) {
        if (!ModList.get().isLoaded(ADVANCED_AE_MODID)) {
            return false;
        }
        return AqeBigCraftingExecutionManager.cancel(host, jobId);
    }

    public static int status(CommandSourceStack source, BlockPos position) {
        if (!ModList.get().isLoaded(ADVANCED_AE_MODID)) {
            source.sendFailure(Component.literal("Advanced AE is not installed."));
            return 0;
        }
        return AqeBigCraftingExecutionManager.statusAt(source, position);
    }

    public static void clear() {
        if (ModList.get().isLoaded(ADVANCED_AE_MODID)) {
            AqeBigCraftingExecutionManager.clear();
        }
    }
}

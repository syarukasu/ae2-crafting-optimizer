package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import com.syaru.ae2craftingoptimizer.optimization.PlanningConfigurationRevisionTracker;
import com.syaru.ae2craftingoptimizer.optimization.ServerPlanningThreadGuard;
import com.syaru.ae2craftingoptimizer.optimization.StorageRevisionTracker;
import net.minecraft.world.level.Level;

/**
 * CraftingCalculation constructorの同期区間で、Pattern graphと参照在庫を同じrevisionへ固定する。
 * workerはここで作成したCapture以外のAE2可変状態を読まない。
 */
public final class Ae2PlanningCaptureCoordinator {
    private Ae2PlanningCaptureCoordinator() {
    }

    public static CaptureBundle capture(
            Level level,
            IGrid grid,
            IActionSource source,
            KeyCounter networkSnapshot,
            AEKey output,
            long requestedAmount,
            StorageRevisionTracker.RevisionToken storageRevision,
            long patternGeneration,
            long recipeGeneration,
            long configurationRevision) {
        // PlannerとShadowの両方が無効な場合は、Pattern APIも在庫も追加走査しない。
        if (!Ae2AuthoritativeCraftingPlanner.planningEnabled()
                && !ACOConfig.enableCraftingEngineShadowMode()) {
            return CaptureBundle.EMPTY;
        }
        if (level == null
                || grid == null
                || source == null
                || networkSnapshot == null
                || output == null
                || requestedAmount <= 0L
                || storageRevision == null
                || !ServerPlanningThreadGuard.canCapture(level)
                || !isCurrent(
                        storageRevision,
                        patternGeneration,
                        recipeGeneration,
                        configurationRevision)) {
            return CaptureBundle.EMPTY;
        }

        Ae2ImmutablePlanningGraphCache.RootCapture graphCapture =
                Ae2ImmutablePlanningGraphCache.capture(grid, level, output);
        // 不完全または別世代のrootを、呼出側世代で再ラベルしない。
        if (graphCapture == null
                || graphCapture.patternGeneration() != patternGeneration
                || graphCapture.recipeGeneration() != recipeGeneration
                || graphCapture.configurationRevision() != configurationRevision) {
            return CaptureBundle.EMPTY;
        }

        Ae2PlanningInventorySnapshot immutableInventory =
                Ae2PlanningInventorySnapshot.captureReferenced(
                        networkSnapshot,
                        graphCapture.referencedKeys(),
                        output);
        // 参照キーの固定中に変化した場合も、旧在庫を新グラフへ結び付けない。
        if (!isCurrent(
                storageRevision,
                patternGeneration,
                recipeGeneration,
                configurationRevision)) {
            return CaptureBundle.EMPTY;
        }

        Ae2CraftingShadowValidator.Capture shadow =
                Ae2CraftingShadowValidator.capturePrepared(
                        output,
                        graphCapture,
                        immutableInventory,
                        storageRevision,
                        configurationRevision);
        Ae2AuthoritativeCraftingPlanner.Capture authoritative =
                Ae2AuthoritativeCraftingPlanner.capturePrepared(
                        level,
                        grid,
                        source,
                        immutableInventory,
                        output,
                        requestedAmount,
                        storageRevision,
                        graphCapture,
                        patternGeneration,
                        recipeGeneration,
                        configurationRevision);
        return new CaptureBundle(shadow, authoritative);
    }

    private static boolean isCurrent(
            StorageRevisionTracker.RevisionToken storageRevision,
            long patternGeneration,
            long recipeGeneration,
            long configurationRevision) {
        return StorageRevisionTracker.isCurrent(storageRevision)
                && ProviderPatternGenerationTracker.generation() == patternGeneration
                && RecipeGenerationTracker.generation() == recipeGeneration
                && PlanningConfigurationRevisionTracker.isCurrent(configurationRevision);
    }

    public record CaptureBundle(
            Ae2CraftingShadowValidator.Capture shadow,
            Ae2AuthoritativeCraftingPlanner.Capture authoritative) {
        private static final CaptureBundle EMPTY = new CaptureBundle(null, null);
    }
}

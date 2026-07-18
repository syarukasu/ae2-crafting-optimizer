package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** 厳格に証明できるAE2 Pattern木から、実行量に依存しないBigInteger Jobを一度だけ構築する。 */
public final class Ae2BigCraftingPlanFactory {
    private Ae2BigCraftingPlanFactory() {
    }

    @Nullable
    public static PreparedBigRootPlan tryCreate(
            Level level,
            IGrid grid,
            IActionSource source,
            AEKey output,
            BigInteger requestedAmount) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(output, "output");
        int maximumBits = ACOConfig.getBigIntegerMaximumBits();
        BigCountMath.requireMaximumBits(requestedAmount, "BigInteger root request", maximumBits);
        if (requestedAmount.signum() <= 0 || Thread.currentThread().isInterrupted()) {
            return null;
        }

        long patternGeneration = ProviderPatternGenerationTracker.generation();
        long recipeGeneration = RecipeGenerationTracker.generation();
        Map<AEKey, BigInteger> inventory = snapshotInventory(grid, source);
        Ae2CompiledCraftingGraphCache.Snapshot snapshot =
                Ae2CompiledCraftingGraphCache.getOrCompile(grid, level);
        if (snapshot.graph().generation() != patternGeneration
                || snapshot.recipeGeneration() != recipeGeneration) {
            return null;
        }
        Ae2StrictCraftingTopology topology = Ae2StrictCraftingTopology.inspect(
                level, grid, inventory.keySet(), snapshot, output);
        if (topology == null) {
            return null;
        }

        Map<AEKey, BigInteger> planningInventory = new LinkedHashMap<>(inventory);
        // AE2同様、完成品そのものは在庫から相殺せず要求されたクラフトとして扱う。
        planningInventory.remove(output);
        PlanningGuard guard = expanded -> {
            if ((expanded & 63) == 0) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new PlanningCancelledException(expanded);
                }
                if (ProviderPatternGenerationTracker.generation() != patternGeneration
                        || RecipeGenerationTracker.generation() != recipeGeneration) {
                    throw new StalePlanningSnapshotException(
                            new PlanningGenerationSnapshot(
                                    patternGeneration, 0L, recipeGeneration),
                            expanded);
                }
            }
        };
        BigCraftingPlan<AEKey> plan = new BigCraftingPlanner<AEKey>(maximumBits).plan(
                snapshot.graph(),
                output,
                requestedAmount,
                planningInventory,
                topology.emittable(),
                guard);
        if (!plan.craftable()
                || plan.patternExecutions().size()
                        > ACOConfig.getCraftingEngineShadowMaximumPatterns()) {
            return null;
        }
        BigInteger bytes = topology.calculateBigExactBytes(
                output, requestedAmount, plan.patternExecutions(), maximumBits);
        if (bytes.signum() <= 0) {
            return null;
        }

        // 計算中に在庫またはPatternが変わった結果は採用しない。
        if (ProviderPatternGenerationTracker.generation() != patternGeneration
                || RecipeGenerationTracker.generation() != recipeGeneration
                || !inventory.equals(snapshotInventory(grid, source))) {
            return null;
        }
        BigCraftingJob<AEKey> job = BigCraftingJob.rootWindowed(
                UUID.randomUUID(),
                output,
                requestedAmount,
                bytes,
                patternGeneration,
                recipeGeneration);
        return new PreparedBigRootPlan(job, plan, bytes, patternGeneration, recipeGeneration);
    }

    private static Map<AEKey, BigInteger> snapshotInventory(IGrid grid, IActionSource source) {
        Map<AEKey, BigInteger> result = new LinkedHashMap<>();
        var storage = grid.getStorageService();
        for (var entry : storage.getCachedInventory()) {
            long amount = appeng.core.AEConfig.instance().isCraftingSimulatedExtraction()
                    ? storage.getInventory().extract(
                            entry.getKey(), entry.getLongValue(), Actionable.SIMULATE, source)
                    : entry.getLongValue();
            if (amount > 0L) {
                result.merge(entry.getKey(), BigInteger.valueOf(amount), BigInteger::add);
            }
        }
        return Map.copyOf(result);
    }

    public record PreparedBigRootPlan(
            BigCraftingJob<AEKey> job,
            BigCraftingPlan<AEKey> symbolicPlan,
            BigInteger reservedBytes,
            long patternGeneration,
            long recipeGeneration) {
        public PreparedBigRootPlan {
            Objects.requireNonNull(job, "job");
            Objects.requireNonNull(symbolicPlan, "symbolicPlan");
            Objects.requireNonNull(reservedBytes, "reservedBytes");
        }
    }
}

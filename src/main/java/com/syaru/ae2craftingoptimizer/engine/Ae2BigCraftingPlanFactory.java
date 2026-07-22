package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** 厳格に証明できるAE2 Pattern木から、実行量に依存しないBigInteger Jobを一度だけ構築する。 */
public final class Ae2BigCraftingPlanFactory {
    /** 64ノードごとに世代と割込みを再検証するためのbit mask。 */
    private static final int GENERATION_CHECK_INTERVAL_MASK = 63;

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
        // 0以下の注文またはキャンセル済みスレッドからBig jobを作らない。
        if (requestedAmount.signum() <= 0 || Thread.currentThread().isInterrupted()) {
            return null;
        }

        long patternGeneration = ProviderPatternGenerationTracker.generation();
        long recipeGeneration = RecipeGenerationTracker.generation();
        Ae2CompiledCraftingGraphCache.Snapshot snapshot =
                Ae2CompiledCraftingGraphCache.getOrCompile(grid, level);
        // Graph構築中にProviderまたはrecipe世代が変わった場合は古いSnapshotを採用しない。
        if (snapshot.graph().generation() != patternGeneration
                || snapshot.recipeGeneration() != recipeGeneration) {
            return null;
        }
        var optionalProgram = snapshot.rootProgram(output);
        // 曖昧、循環、複数出力などを含むルートはBigIntegerでも近似しない。
        if (optionalProgram.isEmpty()) {
            return null;
        }
        CompiledRootProgram<AEKey> program = optionalProgram.get();
        // 通常注文でAE2との一致実績を積んだ同一世代Programだけを巨大注文へ使用する。
        if (!CompiledRootQualificationRegistry.isQualified(
                program,
                ACOConfig.getAuthoritativeMinimumShadowMatches())) {
            return null;
        }

        CompiledRootProgram.InventorySnapshot<AEKey> inventory =
                Ae2ReferencedInventory.captureLive(program, grid, source, output);
        Ae2StrictCraftingTopology topology = snapshot
                .strictTopology(level, grid, program)
                .orElse(null);
        // 実AE2 Pattern API上の完全一致を証明できなければBig jobを作らない。
        if (topology == null
                || !topology.acceptsInventory(grid.getStorageService().getCachedInventory())) {
            return null;
        }

        PlanningGuard guard = expanded -> {
            // 64ノードごとに割込みと世代変更を検出する。
            if ((expanded & GENERATION_CHECK_INTERVAL_MASK) == 0) {
                // 計算キャンセル後は残りのBigInteger演算を行わない。
                if (Thread.currentThread().isInterrupted()) {
                    throw new PlanningCancelledException(expanded);
                }
                // Patternまたはrecipe変更後のProgram結果は破棄する。
                if (ProviderPatternGenerationTracker.generation() != patternGeneration
                        || RecipeGenerationTracker.generation() != recipeGeneration) {
                    throw new StalePlanningSnapshotException(
                            new PlanningGenerationSnapshot(
                                    patternGeneration, 0L, recipeGeneration),
                            expanded);
                }
            }
        };
        BigCraftingPlan<AEKey> plan = program.planBig(
                requestedAmount,
                inventory,
                guard,
                maximumBits);
        // 不足注文または設定したPattern型数上限を超える計画は実行Jobへ変換しない。
        if (!plan.craftable()
                || plan.patternExecutions().size()
                        > ACOConfig.getCraftingEngineShadowMaximumPatterns()) {
            return null;
        }
        BigInteger bytes = topology.calculateBigExactBytes(
                output, requestedAmount, plan.patternExecutions(), maximumBits);
        // 0以下の容量計算結果は破損計画なので予約しない。
        if (bytes.signum() <= 0) {
            return null;
        }

        // 計算中に世代または参照在庫が変わった結果は採用しない。
        if (ProviderPatternGenerationTracker.generation() != patternGeneration
                || RecipeGenerationTracker.generation() != recipeGeneration
                || !Ae2ReferencedInventory.matchesLive(program, inventory, grid, source, output)
                || !topology.remainsValid(grid)) {
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

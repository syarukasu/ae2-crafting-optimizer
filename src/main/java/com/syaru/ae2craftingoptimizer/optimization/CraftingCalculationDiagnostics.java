package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import com.syaru.ae2craftingoptimizer.engine.WideCraftingPlan;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class CraftingCalculationDiagnostics {
    private static final AtomicLong NEXT_CALCULATION_ID = new AtomicLong();

    private CraftingCalculationDiagnostics() {
    }

    /** 同一ログ内で計画開始と結果を相関する、プロセス内だけの単調IDを発行する。 */
    public static long nextCalculationId() {
        return NEXT_CALCULATION_ID.updateAndGet(
                current -> current == Long.MAX_VALUE ? 1L : current + 1L);
    }

    /** CraftingCalculation.runの入口を一件一行で記録し、結果ログと相関できるようにする。 */
    public static void logStarted(
            long calculationId,
            AEKey output,
            long requestedAmount,
            long patternGeneration,
            long recipeGeneration) {
        if (!ACOConfig.logCraftingDecisionFlow()) {
            return;
        }
        AE2CraftingOptimizer.LOGGER.debug(
                "ACO-DIAG event=planning_started calculationId={} output={} requested={} "
                        + "patternGeneration={} recipeGeneration={} thread={}",
                calculationId,
                output == null ? "<unknown>" : output.getId(),
                requestedAmount,
                patternGeneration < 0L ? "<unknown>" : patternGeneration,
                recipeGeneration < 0L ? "<unknown>" : recipeGeneration,
                Thread.currentThread().getName());
    }

    /** 計算結果を一件一行で記録し、AE2標準経路とACO採用経路を区別可能にする。 */
    public static void logDecision(
            long calculationId,
            AEKey output,
            long requestedAmount,
            ICraftingPlan plan,
            long elapsedNanos,
            String plannerRoute,
            long patternGeneration,
            long recipeGeneration,
            CraftingFallbackDiagnostics.Observation fallback) {
        if (!ACOConfig.logCraftingDecisionFlow()) {
            return;
        }
        WideCraftingPlan sidecar = plan == null
                ? null
                : Ae2CraftingPlanSidecars.metadata(plan).orElse(null);
        AE2CraftingOptimizer.LOGGER.debug(
                "ACO-DIAG event=planning_complete calculationId={} route={} output={} requested={} "
                        + "simulation={} missingEntries={} facadeBytes={} sidecar={} exactBytes={} "
                        + "patternGeneration={} recipeGeneration={} fallbackReason={} fallbackAggregate={} "
                        + "elapsedMs={} thread={}",
                calculationId,
                plannerRoute,
                output == null ? "<unknown>" : output.getId(),
                requestedAmount,
                plan == null ? "<none>" : plan.simulation(),
                plan == null ? -1 : plan.missingItems().size(),
                plan == null ? -1L : plan.bytes(),
                sidecar == null ? "none" : sidecar.getClass().getSimpleName(),
                sidecar == null ? "none" : formatExactAmount(sidecar.exactBytes()),
                patternGeneration < 0L ? "<unknown>" : patternGeneration,
                recipeGeneration < 0L ? "<unknown>" : recipeGeneration,
                fallback.code(),
                fallback.aggregateCount(),
                TimeUnit.NANOSECONDS.toMillis(elapsedNanos),
                Thread.currentThread().getName());
    }

    /** exact値をログ量に比例させず、long範囲またはbit長へ正規化する。 */
    static String formatExactAmount(BigInteger amount) {
        if (amount == null) {
            return "none";
        }
        // signed long内は値そのものを表示し、境界試験をログだけで判定可能にする。
        if (amount.bitLength() <= 63) {
            return amount.toString();
        }
        return "sign=" + amount.signum() + ",bits=" + amount.bitLength();
    }

    public static void logIfSlow(
            AEKey output,
            long requestedAmount,
            ICraftingPlan plan,
            long elapsedNanos,
            String plannerRoute,
            CraftingFallbackDiagnostics.Observation fallback) {
        if (!ACOConfig.logSlowCraftCalculations()) {
            return;
        }

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        if (elapsedMillis < ACOConfig.getSlowCraftCalculationMillis()) {
            return;
        }

        GenericStack finalOutput = plan != null ? plan.finalOutput() : null;
        int missingCount = plan != null ? plan.missingItems().size() : -1;
        long bytes = plan != null ? plan.bytes() : -1L;
        AE2CraftingOptimizer.LOGGER.info(
                "ACO-DIAG event=planning_slow output={} requested={} final={} missingEntries={} facadeBytes={} "
                        + "elapsedMs={} route={} fallbackReason={} patternGeneration={} recipeGeneration={} fallbackAggregate={}",
                output.getId(),
                requestedAmount,
                finalOutput,
                missingCount,
                bytes,
                elapsedMillis,
                plannerRoute,
                fallback.code(),
                fallback.patternGeneration(),
                fallback.recipeGeneration(),
                fallback.aggregateCount());
    }
}

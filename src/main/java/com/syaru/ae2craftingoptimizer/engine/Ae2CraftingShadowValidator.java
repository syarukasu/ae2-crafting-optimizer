package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.util.LinkedHashMap;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.Level;

public final class Ae2CraftingShadowValidator {
    private static final int MAX_LOGGED_MISMATCHES = 64;
    private static final AtomicInteger LOGGED_MISMATCHES = new AtomicInteger();
    private static final Set<String> LOGGED_KEYS = ConcurrentHashMap.newKeySet();

    private Ae2CraftingShadowValidator() {
    }

    public static Capture capture(Level level, IGrid grid) {
        if (!ACOConfig.enableCraftingEngineShadowMode() || level == null || grid == null) {
            return null;
        }
        try {
            return new Capture(
                    grid,
                    Ae2CompiledCraftingGraphCache.getOrCompile(grid, level),
                    counterMap(grid.getStorageService().getCachedInventory()),
                    ProviderPatternGenerationTracker.generation(),
                    RecipeGenerationTracker.generation());
        } catch (RuntimeException | LinkageError failure) {
            OptimizationMetrics.recordCraftingEngineShadowSkipped();
            String key = failure.getClass().getName() + ":capture";
            if (ACOConfig.logCraftingEngineShadowMismatches() && LOGGED_KEYS.add(key)) {
                AE2CraftingOptimizer.LOGGER.debug(
                        "ACO Shadow Mode could not capture an immutable input snapshot: {}",
                        failure.toString());
            }
            return null;
        }
    }

    public static void validate(Capture capture, AEKey output, long requestedAmount, ICraftingPlan reference) {
        if (!ACOConfig.enableCraftingEngineShadowMode()
                || capture == null
                || output == null
                || reference == null
                || reference.patternTimes().size() > ACOConfig.getCraftingEngineShadowMaximumPatterns()) {
            return;
        }
        try {
            if (capture.patternGeneration() != ProviderPatternGenerationTracker.generation()
                    || capture.recipeGeneration() != RecipeGenerationTracker.generation()
                    || !capture.inventory().equals(counterMap(
                            capture.grid().getStorageService().getCachedInventory()))) {
                OptimizationMetrics.recordCraftingEngineShadowSkipped();
                return;
            }
            Set<AEKey> emittable = reference.emittedItems().keySet();
            Map<AEKey, BigInteger> bigInventory = new LinkedHashMap<>();
            capture.inventory().forEach((key, amount) -> bigInventory.put(key, BigInteger.valueOf(amount)));
            // CraftingCalculationと同じく、完成品自身の在庫は要求充当へ使わない。
            bigInventory.remove(output);
            var result = new OverflowPromotingCraftingPlanner<AEKey>().plan(
                    capture.snapshot().graph(),
                    output,
                    BigInteger.valueOf(requestedAmount),
                    bigInventory,
                    emittable);
            if (!(result instanceof OverflowPromotingCraftingPlanner.LongResult<?> longResult)) {
                OptimizationMetrics.recordCraftingEngineShadowOverflow();
                return;
            }
            @SuppressWarnings("unchecked")
            LongCraftingPlan<AEKey> shadow = (LongCraftingPlan<AEKey>) longResult.plan();

            Map<String, Long> referencePatterns = new LinkedHashMap<>();
            reference.patternTimes().forEach((pattern, executions) -> {
                String id = capture.snapshot().id(pattern);
                if (id != null) {
                    CheckedLongMath.merge(referencePatterns, id, executions, "shadow/referencePattern");
                }
            });
            var comparison = CraftingPlanShadowComparator.compare(
                    shadow, referencePatterns, counterMap(reference.missingItems()));
            OptimizationMetrics.recordCraftingEngineShadowComparison(comparison.matches());
            if (!comparison.matches()) {
                logMismatch(output, requestedAmount, comparison.mismatches());
            }
        } catch (CountOverflowException overflow) {
            OptimizationMetrics.recordCraftingEngineShadowOverflow();
        } catch (Throwable throwable) {
            OptimizationMetrics.recordCraftingEngineShadowSkipped();
            String key = throwable.getClass().getName() + ':' + output.getId();
            if (ACOConfig.logCraftingEngineShadowMismatches() && LOGGED_KEYS.add(key)) {
                AE2CraftingOptimizer.LOGGER.debug(
                        "ACO Shadow Mode skipped {} x{}: {}",
                        output.getId(), requestedAmount, throwable.toString());
            }
        }
    }

    public static void resetDiagnostics() {
        LOGGED_MISMATCHES.set(0);
        LOGGED_KEYS.clear();
    }

    private static Map<AEKey, Long> counterMap(KeyCounter counter) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        mergeCounter(result, counter, "shadow/counter");
        return result;
    }

    private static void mergeCounter(Map<AEKey, Long> target, KeyCounter counter, String context) {
        for (var entry : counter) {
            CheckedLongMath.merge(target, entry.getKey(), entry.getLongValue(), context);
        }
    }

    private static void logMismatch(AEKey output, long requestedAmount, List<String> mismatches) {
        if (!ACOConfig.logCraftingEngineShadowMismatches()
                || LOGGED_MISMATCHES.get() >= MAX_LOGGED_MISMATCHES) {
            return;
        }
        String key = output.getId() + ":" + mismatches;
        if (LOGGED_KEYS.add(key) && LOGGED_MISMATCHES.incrementAndGet() <= MAX_LOGGED_MISMATCHES) {
            AE2CraftingOptimizer.LOGGER.warn(
                    "ACO Shadow Mode difference for {} x{} (AE2 result remains authoritative): {}",
                    output.getId(), requestedAmount, mismatches);
        }
    }

    public record Capture(
            IGrid grid,
            Ae2CompiledCraftingGraphCache.Snapshot snapshot,
            Map<AEKey, Long> inventory,
            long patternGeneration,
            long recipeGeneration) {
        public Capture {
            java.util.Objects.requireNonNull(grid, "grid");
            inventory = Map.copyOf(inventory);
            if (patternGeneration < 0L || recipeGeneration < 0L) {
                throw new IllegalArgumentException("planning generations must not be negative");
            }
        }

        @Override
        public Map<AEKey, Long> inventory() {
            return inventory;
        }
    }
}

package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** AE2標準計画を正としてRoot Programの全会計を比較し、同一世代Programの採用実績を蓄積する。 */
public final class Ae2CraftingShadowValidator {
    /** 同じ問題でログを埋めないための、一起動当たりの差異ログ上限。 */
    private static final int MAX_LOGGED_MISMATCHES = 64;
    private static final AtomicInteger LOGGED_MISMATCHES = new AtomicInteger();
    private static final Set<String> LOGGED_KEYS = ConcurrentHashMap.newKeySet();

    private Ae2CraftingShadowValidator() {
    }

    @Nullable
    public static Capture capture(
            Level level,
            IGrid grid,
            IActionSource source,
            KeyCounter networkSnapshot,
            AEKey output) {
        // Shadow Mode無効または必要参照欠落時は観測用計算を作らない。
        if (!ACOConfig.enableCraftingEngineShadowMode()
                || level == null
                || grid == null
                || source == null
                || networkSnapshot == null
                || output == null) {
            return null;
        }
        try {
            Ae2CompiledCraftingGraphCache.Snapshot graphSnapshot =
                    Ae2CompiledCraftingGraphCache.getOrCompile(grid, level);
            var optionalProgram = graphSnapshot.rootProgram(output);
            // 数式化できない経路はShadow比較対象にもせず、AE2だけを実行する。
            if (optionalProgram.isEmpty()) {
                OptimizationMetrics.recordCraftingEngineShadowSkipped();
                return null;
            }
            CompiledRootProgram<AEKey> program = optionalProgram.get();
            return new Capture(
                    grid,
                    source,
                    output,
                    graphSnapshot,
                    program,
                    Ae2ReferencedInventory.captureNetworkSnapshot(
                            program,
                            networkSnapshot,
                            output),
                    ProviderPatternGenerationTracker.generation(),
                    RecipeGenerationTracker.generation());
        } catch (RuntimeException | LinkageError failure) {
            OptimizationMetrics.recordCraftingEngineShadowSkipped();
            String key = failure.getClass().getName() + ":capture";
            // 同じcapture失敗は一度だけdebugへ残す。
            if (ACOConfig.logCraftingEngineShadowMismatches() && LOGGED_KEYS.add(key)) {
                AE2CraftingOptimizer.LOGGER.debug(
                        "ACO Shadow Mode could not capture referenced input keys: {}",
                        failure.toString());
            }
            return null;
        }
    }

    public static void validate(
            @Nullable Capture capture,
            AEKey output,
            long requestedAmount,
            CalculationStrategy strategy,
            ICraftingPlan reference) {
        // CRAFT_LESSは部分成功量の探索規則が異なるため、Authoritative認定には使わない。
        if (!ACOConfig.enableCraftingEngineShadowMode()
                || capture == null
                || output == null
                || strategy != CalculationStrategy.REPORT_MISSING_ITEMS
                || reference == null
                || reference.patternTimes().size()
                        > ACOConfig.getCraftingEngineShadowMaximumPatterns()) {
            return;
        }
        try {
            // Pattern世代またはrecipe世代が変わった比較は、現在Programの実績へ加えない。
            if (capture.patternGeneration() != ProviderPatternGenerationTracker.generation()
                    || capture.recipeGeneration() != RecipeGenerationTracker.generation()) {
                OptimizationMetrics.recordCraftingEngineShadowSkipped();
                return;
            }
            // 計算中に参照キーの在庫が変わった場合も、比較条件が同一でないためスキップする。
            if (!Ae2ReferencedInventory.matchesLive(
                    capture.program(),
                    capture.inventory(),
                    capture.grid(),
                    capture.source(),
                    capture.output())) {
                OptimizationMetrics.recordCraftingEngineShadowSkipped();
                return;
            }

            var result = new OverflowPromotingCraftingPlanner<AEKey>(
                    ACOConfig.getBigIntegerMaximumBits()).plan(
                    capture.program(),
                    BigInteger.valueOf(requestedAmount),
                    capture.inventory(),
                    PlanningGuard.none());
            // AE2のlong計画と同じ境界で比較できないoverflow注文は認定実績へ加えない。
            if (!(result instanceof OverflowPromotingCraftingPlanner.LongResult<?> longResult)) {
                OptimizationMetrics.recordCraftingEngineShadowOverflow();
                return;
            }
            @SuppressWarnings("unchecked")
            LongCraftingPlan<AEKey> shadow = (LongCraftingPlan<AEKey>) longResult.plan();

            Map<String, Long> referencePatterns = new LinkedHashMap<>();
            boolean allPatternsMapped = true;
            // AE2計画の実Pattern参照を、同じ世代Graphの安定fingerprintへ変換する。
            for (Map.Entry<appeng.api.crafting.IPatternDetails, Long> entry
                    : reference.patternTimes().entrySet()) {
                String id = capture.programSnapshotId(entry.getKey());
                // fingerprintへ戻せないPatternが一つでもあれば、Graphが完全ではないため不一致とする。
                if (id == null) {
                    allPatternsMapped = false;
                    break;
                }
                CheckedLongMath.merge(
                        referencePatterns,
                        id,
                        entry.getValue(),
                        "shadow/reference-pattern");
            }

            var comparison = CraftingPlanShadowComparator.compareComplete(
                    shadow,
                    referencePatterns,
                    counterMap(reference.usedItems()),
                    counterMap(reference.emittedItems()),
                    counterMap(reference.missingItems()));
            List<String> mismatches = new ArrayList<>(comparison.mismatches());
            // 未登録Patternがあれば、見えているMapだけが一致しても認定しない。
            if (!allPatternsMapped) {
                mismatches.add("reference contains a pattern absent from the compiled generation snapshot");
            }
            // 最終出力キーと数量も一致しなければ、同じ注文の結果とはみなさない。
            if (!reference.finalOutput().what().equals(output)
                    || reference.finalOutput().amount() != requestedAmount) {
                mismatches.add("final output differs");
            }
            // 不足の有無とAE2 simulationフラグが一致しなければ計画状態が異なる。
            if (reference.simulation() != !shadow.craftable()) {
                mismatches.add("simulation state differs");
            }
            // 決定的な単一路線なのにAE2が複数経路を報告した場合は証明条件が不足している。
            if (reference.multiplePaths()) {
                mismatches.add("AE2 reported multiple crafting paths");
            }

            boolean matches = mismatches.isEmpty();
            OptimizationMetrics.recordCraftingEngineShadowComparison(matches);
            // 完全一致した同一世代Programだけ一致回数を増やす。
            if (matches) {
                CompiledRootQualificationRegistry.recordMatch(capture.program());
            } else {
                CompiledRootQualificationRegistry.recordMismatch(capture.program());
                logMismatch(output, requestedAmount, mismatches);
            }
        } catch (CountOverflowException overflow) {
            OptimizationMetrics.recordCraftingEngineShadowOverflow();
        } catch (Throwable throwable) {
            OptimizationMetrics.recordCraftingEngineShadowSkipped();
            String key = throwable.getClass().getName() + ':' + output.getId();
            // 同じ出力と例外型のShadow skip理由は一度だけdebugへ残す。
            if (ACOConfig.logCraftingEngineShadowMismatches() && LOGGED_KEYS.add(key)) {
                AE2CraftingOptimizer.LOGGER.debug(
                        "ACO Shadow Mode skipped {} x{}: {}",
                        output.getId(),
                        requestedAmount,
                        throwable.toString());
            }
        }
    }

    public static void resetDiagnostics() {
        LOGGED_MISMATCHES.set(0);
        LOGGED_KEYS.clear();
    }

    private static Map<AEKey, Long> counterMap(KeyCounter counter) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        // AE2の最終計画Counterだけを比較用Mapへ変換する。
        for (var entry : counter) {
            CheckedLongMath.merge(
                    result,
                    entry.getKey(),
                    entry.getLongValue(),
                    "shadow/counter");
        }
        return Map.copyOf(result);
    }

    private static void logMismatch(
            AEKey output,
            long requestedAmount,
            List<String> mismatches) {
        // 設定OFFまたは上限到達後は追加ログを出さない。
        if (!ACOConfig.logCraftingEngineShadowMismatches()
                || LOGGED_MISMATCHES.get() >= MAX_LOGGED_MISMATCHES) {
            return;
        }
        String key = output.getId() + ":" + mismatches;
        // 同じ差異内容は一度だけ、かつ全体上限内で警告する。
        if (LOGGED_KEYS.add(key)
                && LOGGED_MISMATCHES.incrementAndGet() <= MAX_LOGGED_MISMATCHES) {
            AE2CraftingOptimizer.LOGGER.warn(
                    "ACO Shadow Mode difference for {} x{} (AE2 result remains authoritative): {}",
                    output.getId(),
                    requestedAmount,
                    mismatches);
        }
    }

    public record Capture(
            IGrid grid,
            IActionSource source,
            AEKey output,
            Ae2CompiledCraftingGraphCache.Snapshot graphSnapshot,
            CompiledRootProgram<AEKey> program,
            CompiledRootProgram.InventorySnapshot<AEKey> inventory,
            long patternGeneration,
            long recipeGeneration) {
        public Capture {
            java.util.Objects.requireNonNull(grid, "grid");
            java.util.Objects.requireNonNull(source, "source");
            java.util.Objects.requireNonNull(output, "output");
            java.util.Objects.requireNonNull(graphSnapshot, "graphSnapshot");
            java.util.Objects.requireNonNull(program, "program");
            java.util.Objects.requireNonNull(inventory, "inventory");
            // 負の世代値はSnapshot識別へ使用できないため拒否する。
            if (patternGeneration < 0L || recipeGeneration < 0L) {
                throw new IllegalArgumentException("planning generations must not be negative");
            }
        }

        @Nullable
        private String programSnapshotId(appeng.api.crafting.IPatternDetails pattern) {
            return graphSnapshot.id(pattern);
        }
    }
}

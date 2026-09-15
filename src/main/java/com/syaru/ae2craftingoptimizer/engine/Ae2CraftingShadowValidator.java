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
import com.syaru.ae2craftingoptimizer.optimization.PlanningConfigurationRevisionTracker;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import com.syaru.ae2craftingoptimizer.optimization.StorageRevisionTracker;
import com.syaru.ae2craftingoptimizer.optimization.ServerPlanningThreadGuard;
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
    /** 例外型と出力キーの組み合わせを保持する診断索引の固定上限。 */
    private static final int MAX_LOGGED_SKIP_KEYS = 4096;
    private static final AtomicInteger LOGGED_MISMATCHES = new AtomicInteger();
    private static final Set<String> LOGGED_MISMATCH_KEYS = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_SKIP_KEYS = ConcurrentHashMap.newKeySet();

    private Ae2CraftingShadowValidator() {
    }

    @Nullable
    public static Capture capture(
            Level level,
            IGrid grid,
            IActionSource source,
            KeyCounter networkSnapshot,
            AEKey output,
            StorageRevisionTracker.RevisionToken storageRevision) {
        // 公開互換入口でもmutable KeyCounterは呼出thread内で一度だけ不変化する。
        if (networkSnapshot == null
                || !ServerPlanningThreadGuard.canCapture(level)) {
            return null;
        }
        return capture(
                level,
                grid,
                source,
                Ae2PlanningInventorySnapshot.capture(networkSnapshot),
                output,
                storageRevision);
    }

    @Nullable
    public static Capture capture(
            Level level,
            IGrid grid,
            IActionSource source,
            Ae2PlanningInventorySnapshot networkSnapshot,
            AEKey output,
            StorageRevisionTracker.RevisionToken storageRevision) {
        // Shadow Mode無効または必要参照欠落時は観測用計算を作らない。
        if (!ACOConfig.enableCraftingEngineShadowMode()
                || level == null
                || grid == null
                || source == null
                || networkSnapshot == null
                || output == null
                || storageRevision == null
                || !ServerPlanningThreadGuard.canCapture(level)) {
            return null;
        }
        try {
            Ae2ImmutablePlanningGraphCache.RootCapture graphCapture =
                    Ae2ImmutablePlanningGraphCache.capture(grid, level, output);
            // revision窓で固定できなかった要求はShadow教材に使わない。
            if (graphCapture == null) {
                OptimizationMetrics.recordCraftingEngineShadowSkipped();
                return null;
            }
            return capturePrepared(
                    output,
                    graphCapture,
                    networkSnapshot,
                    storageRevision,
                    PlanningConfigurationRevisionTracker.current());
        } catch (RuntimeException | LinkageError failure) {
            OptimizationMetrics.recordCraftingEngineShadowSkipped();
            String key = failure.getClass().getName() + ":capture";
            // 同じcapture失敗は一度だけdebugへ残す。
            if (ACOConfig.logCraftingEngineShadowMismatches() && rememberSkipKey(key)) {
                AE2CraftingOptimizer.LOGGER.debug(
                        "ACO Shadow Mode could not capture referenced input keys: {}",
                        failure.toString());
            }
            return null;
        }
    }

    @Nullable
    static Capture capturePrepared(
            AEKey output,
            Ae2ImmutablePlanningGraphCache.RootCapture graphCapture,
            Ae2PlanningInventorySnapshot networkSnapshot,
            StorageRevisionTracker.RevisionToken storageRevision,
            long configurationRevision) {
        // Shadow無効時は追加計算を作らず、主Plannerの結果にも影響させない。
        if (!ACOConfig.enableCraftingEngineShadowMode()
                || !PlanningConfigurationRevisionTracker.isCurrent(
                        configurationRevision)) {
            return null;
        }
        return new Capture(
                output,
                graphCapture,
                networkSnapshot,
                graphCapture.patternGeneration(),
                graphCapture.recipeGeneration(),
                storageRevision,
                configurationRevision);
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
            // 比較開始前に三世代のどれかが変わった教材は認定へ使わない。
            if (!isCaptureCurrent(capture)) {
                OptimizationMetrics.recordCraftingEngineShadowSkipped();
                return;
            }
            Ae2PlanningGraphSnapshot graphSnapshot = capture.graphCapture().compile();
            CompiledRootProgram.Outcome<AEKey> rootOutcome =
                    graphSnapshot.rootProgramOutcome(output);
            // 数式化できない経路はShadow比較対象にせず、AE2結果だけを正本とする。
            if (rootOutcome.program().isEmpty()) {
                OptimizationMetrics.recordCraftingEngineShadowSkipped();
                return;
            }
            CompiledRootProgram<AEKey> program = rootOutcome.program().orElseThrow();
            Ae2StrictCraftingTopology topology = graphSnapshot.strictTopology(program).orElse(null);
            // exact input domainを証明できないPatternはAE2候補選択と比較できない。
            if (topology == null) {
                OptimizationMetrics.recordCraftingEngineShadowSkipped();
                return;
            }
            CompiledRootProgram.InventorySnapshot<AEKey> inventory =
                    Ae2ReferencedInventory.captureNetworkSnapshot(
                            program,
                            capture.networkSnapshot(),
                            output);

            var result = new OverflowPromotingCraftingPlanner<AEKey>(
                    ACOConfig.getBigIntegerMaximumBits()).plan(
                    program,
                    BigInteger.valueOf(requestedAmount),
                    inventory,
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
                String id = graphSnapshot.id(entry.getKey());
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

            long shadowBytes = topology.calculateAe2LongBytes(
                    output,
                    requestedAmount,
                    shadow.patternExecutions());
            var comparison = CraftingPlanShadowComparator.compareComplete(
                    shadow,
                    shadowBytes,
                    referencePatterns,
                    counterMap(reference.usedItems()),
                    counterMap(reference.emittedItems()),
                    counterMap(reference.missingItems()),
                    reference.bytes());
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

            // 比較中に三世代のどれかが変わった結果も、同じProgramの実績へ加えない。
            if (!isCaptureCurrent(capture)) {
                OptimizationMetrics.recordCraftingEngineShadowSkipped();
                return;
            }
            boolean matches = mismatches.isEmpty();
            OptimizationMetrics.recordCraftingEngineShadowComparison(matches);
            // 完全一致した同一世代Programだけ一致回数を増やす。
            if (matches) {
                CompiledRootQualificationRegistry.recordMatch(program);
            } else {
                CompiledRootQualificationRegistry.recordMismatch(program);
                logMismatch(output, requestedAmount, mismatches);
            }
        } catch (CountOverflowException overflow) {
            OptimizationMetrics.recordCraftingEngineShadowOverflow();
        } catch (RuntimeException | LinkageError throwable) {
            OptimizationMetrics.recordCraftingEngineShadowSkipped();
            String key = throwable.getClass().getName() + ':' + output.getId();
            // 同じ出力と例外型のShadow skip理由は一度だけdebugへ残す。
            if (ACOConfig.logCraftingEngineShadowMismatches() && rememberSkipKey(key)) {
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
        LOGGED_MISMATCH_KEYS.clear();
        LOGGED_SKIP_KEYS.clear();
    }

    private static boolean isCaptureCurrent(Capture capture) {
        return capture.patternGeneration() == ProviderPatternGenerationTracker.generation()
                && capture.recipeGeneration() == RecipeGenerationTracker.generation()
                && StorageRevisionTracker.isCurrent(capture.storageRevision())
                && PlanningConfigurationRevisionTracker.isCurrent(
                        capture.configurationRevision());
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
        if (reserveMismatchLog(key)) {
            AE2CraftingOptimizer.LOGGER.warn(
                    "ACO Shadow Mode difference for {} x{} (AE2 result remains authoritative): {}",
                    output.getId(),
                    requestedAmount,
                    mismatches);
        }
    }

    /** 低頻度の診断経路だけを同期し、通常Planner hot pathへロックを持ち込まない。 */
    static boolean rememberSkipKey(String key) {
        synchronized (LOGGED_SKIP_KEYS) {
            // 世代や出力が増え続けても、診断索引を固定上限より大きく保持しない。
            if (LOGGED_SKIP_KEYS.size() >= MAX_LOGGED_SKIP_KEYS) {
                LOGGED_SKIP_KEYS.clear();
            }
            return LOGGED_SKIP_KEYS.add(key);
        }
    }

    /** 差異ログの全体上限と重複排除を一つの原子的な診断操作として扱う。 */
    private static boolean reserveMismatchLog(String key) {
        synchronized (LOGGED_MISMATCH_KEYS) {
            // 上限到達後は新しいキーも保持せず、常駐量とログ件数を同時に止める。
            if (LOGGED_MISMATCHES.get() >= MAX_LOGGED_MISMATCHES
                    || !LOGGED_MISMATCH_KEYS.add(key)) {
                return false;
            }
            LOGGED_MISMATCHES.incrementAndGet();
            return true;
        }
    }

    static int loggedSkipKeyCount() {
        synchronized (LOGGED_SKIP_KEYS) {
            return LOGGED_SKIP_KEYS.size();
        }
    }

    public record Capture(
            AEKey output,
            Ae2ImmutablePlanningGraphCache.RootCapture graphCapture,
            Ae2PlanningInventorySnapshot networkSnapshot,
            long patternGeneration,
            long recipeGeneration,
            StorageRevisionTracker.RevisionToken storageRevision,
            long configurationRevision) {
        public Capture {
            java.util.Objects.requireNonNull(output, "output");
            java.util.Objects.requireNonNull(graphCapture, "graphCapture");
            java.util.Objects.requireNonNull(networkSnapshot, "networkSnapshot");
            java.util.Objects.requireNonNull(storageRevision, "storageRevision");
            // 負の世代値はSnapshot識別へ使用できないため拒否する。
            if (patternGeneration < 0L || recipeGeneration < 0L) {
                throw new IllegalArgumentException("planning generations must not be negative");
            }
            if (configurationRevision <= 0L) {
                throw new IllegalArgumentException("configuration revision must be positive");
            }
        }

    }
}

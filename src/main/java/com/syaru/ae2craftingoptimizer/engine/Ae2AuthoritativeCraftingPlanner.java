package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * AE2標準計画とのShadow一致実績があり、厳密に証明できるRoot Programだけを置き換えるPlanner。
 * 条件を一つでも満たせない場合はnullを返し、呼出側がAE2標準経路を実行する。
 */
public final class Ae2AuthoritativeCraftingPlanner {
    /** 64ノードごとに世代と割込みを再検証するためのbit mask。 */
    private static final int GENERATION_CHECK_INTERVAL_MASK = 63;
    private static final Set<String> LOGGED_FALLBACKS = ConcurrentHashMap.newKeySet();

    private Ae2AuthoritativeCraftingPlanner() {
    }

    @Nullable
    public static Capture capture(
            Level level,
            IGrid grid,
            IActionSource source,
            KeyCounter networkSnapshot) {
        // 高速経路OFF、必要参照欠落、ActionSource欠落時はAE2標準計算だけを使う。
        if (!planningEnabled()
                || level == null
                || grid == null
                || source == null
                || networkSnapshot == null) {
            return null;
        }
        return new Capture(
                level,
                grid,
                source,
                networkSnapshot,
                ProviderPatternGenerationTracker.generation(),
                RecipeGenerationTracker.generation());
    }

    @Nullable
    public static ICraftingPlan tryPlan(
            @Nullable Capture capture,
            AEKey output,
            long requestedAmount,
            CalculationStrategy strategy) {
        // 無効設定、不正引数、キャンセル済み計算はAE2標準経路へ戻す。
        if (!planningEnabled()
                || capture == null
                || output == null
                || strategy == null
                || requestedAmount <= 0L
                || Thread.currentThread().isInterrupted()) {
            return null;
        }

        try {
            capture.requireCurrentGenerations();
            Ae2CompiledCraftingGraphCache.Snapshot graphSnapshot =
                    Ae2CompiledCraftingGraphCache.getOrCompile(capture.grid(), capture.level());
            var optionalProgram = graphSnapshot.rootProgram(output);
            // 曖昧、循環、複数出力などを含むルートはコンパイルせずAE2へ戻す。
            if (optionalProgram.isEmpty()) {
                return null;
            }
            CompiledRootProgram<AEKey> program = optionalProgram.get();
            Ae2StrictCraftingTopology topology = graphSnapshot
                    .strictTopology(capture.level(), capture.grid(), program)
                    .orElse(null);
            // 実AE2 Pattern API上の完全一致を証明できない場合はAE2へ戻す。
            if (topology == null || !topology.acceptsInventory(capture.inventorySnapshot())) {
                return null;
            }
            boolean wideArithmeticRequired = topology.mightRequireWideArithmetic(
                    output,
                    BigInteger.valueOf(requestedAmount),
                    ACOConfig.getBigIntegerMaximumBits());
            boolean shadowQualified = CompiledRootQualificationRegistry.isQualified(
                    program,
                    ACOConfig.getAuthoritativeMinimumShadowMatches());
            /*
             * 通常計画は同世代のAE2 Shadow一致を必須にする。
             * AE2自身が完走できないlong超過計画だけは、設定で許可した場合に限り、
             * 厳密Topology・世代・参照在庫の証明をShadowの代わりに使う。
             */
            if (!shadowQualified
                    && (!wideArithmeticRequired
                            || ACOConfig.requireAqeBigPlanShadowQualification())) {
                return null;
            }
            // Atomic専用運用では、wide演算が不要な通常注文を直ちにAE2へ戻す。
            if (!ACOConfig.enableAuthoritativeCompiledPlanner() && !wideArithmeticRequired) {
                return null;
            }

            BigKeyCounterSidecars.Snapshot inventoryMetadata =
                    BigKeyCounterSidecars.snapshot(capture.inventorySnapshot()).orElse(null);
            // Adapter失敗を含む不完全Snapshotは、飽和値から不足数を推測せずAE2へ戻す。
            if (inventoryMetadata != null && !inventoryMetadata.complete()) {
                return null;
            }
            CompiledRootProgram.BigInventorySnapshot<AEKey> exactPlanningInventory =
                    Ae2ReferencedInventory.captureExactNetworkSnapshot(
                            program,
                            capture.inventorySnapshot(),
                            output);
            CompiledRootProgram.InventorySnapshot<AEKey> planningInventory = null;
            // 正確なSidecarが無い通常AE2環境だけ、従来のlong Snapshotを使用する。
            if (exactPlanningInventory == null) {
                planningInventory = Ae2ReferencedInventory.captureNetworkSnapshot(
                        program,
                        capture.inventorySnapshot(),
                        output);
            }
            PlanningGuard guard = expanded -> {
                // 64ノードごとに世代変更とスレッド割込みを確認し、古い結果を早めに破棄する。
                if ((expanded & GENERATION_CHECK_INTERVAL_MASK) == 0) {
                    capture.requireCurrentGenerations();
                    // 計算キャンセル後は残りノードを処理しない。
                    if (Thread.currentThread().isInterrupted()) {
                        throw new PlanningCancelledException(expanded);
                    }
                }
            };
            OverflowPromotingCraftingPlanner<AEKey> planner =
                    new OverflowPromotingCraftingPlanner<>(
                            ACOConfig.getBigIntegerMaximumBits());
            var promoted = exactPlanningInventory != null
                    ? planner.plan(
                            program,
                            BigInteger.valueOf(requestedAmount),
                            exactPlanningInventory,
                            guard)
                    : planner.plan(
                            program,
                            BigInteger.valueOf(requestedAmount),
                            planningInventory,
                            guard);
            // Root Program経路以外の結果はAuthoritativeとして採用しない。
            if (!promoted.provenEquivalent()) {
                return null;
            }
            NormalizedPlan symbolic = normalize(promoted);
            ICraftingPlan result;
            // 個別値がlongを超える場合、標準AE2 Jobへ丸めずAQE専用のBig親Jobを作る。
            if (symbolic == null) {
                result = createBigIntegerParentPlan(
                        capture,
                        graphSnapshot,
                        program,
                        topology,
                        output,
                        requestedAmount,
                        strategy,
                        promoted);
                // 厳格な親Jobへ変換できない経路は、値を近似せずAE2本来の計算へ戻す。
                if (result == null) {
                    return null;
                }
            } else {
                result = createLongFacadePlan(
                        capture,
                        graphSnapshot,
                        topology,
                        output,
                        requestedAmount,
                        strategy,
                        symbolic);
                // AtomicまたはAuthoritative設定で採用できない通常計画はAE2へ戻す。
                if (result == null) {
                    return null;
                }
            }

            capture.requireCurrentGenerations();
            // 計算で参照したキーだけでも在庫が変わっていれば、古い結果を返さない。
            boolean inventoryStillMatches = exactPlanningInventory != null
                    ? Ae2ReferencedInventory.matchesLive(
                            program,
                            exactPlanningInventory,
                            capture.grid(),
                            capture.source(),
                            output)
                    : Ae2ReferencedInventory.matchesLive(
                            program,
                            planningInventory,
                            capture.grid(),
                            capture.source(),
                            output);
            if (!inventoryStillMatches) {
                return null;
            }
            // Emitterまたはファジー候補が変わった場合も、AE2と選択結果がずれるため破棄する。
            if (!topology.remainsValid(capture.grid())) {
                return null;
            }
            return result;
        } catch (PlanningCancelledException
                | StalePlanningSnapshotException
                | ArithmeticException ignored) {
            return null;
        } catch (Throwable failure) {
            logFallbackOnce(output, failure);
            return null;
        }
    }

    @Nullable
    private static ICraftingPlan createBigIntegerParentPlan(
            Capture capture,
            Ae2CompiledCraftingGraphCache.Snapshot graphSnapshot,
            CompiledRootProgram<AEKey> program,
            Ae2StrictCraftingTopology topology,
            AEKey output,
            long requestedAmount,
            CalculationStrategy strategy,
            OverflowPromotingCraftingPlanner.Result<AEKey> promoted) {
        // 個別long超過はBigInteger Plannerの正確な結果からだけ親Jobへ変換する。
        if (!(promoted instanceof OverflowPromotingCraftingPlanner.BigResult<AEKey> bigResult)
                || !ACOConfig.enableBigIntegerGameplayExecution()) {
            return null;
        }
        BigCraftingPlan<AEKey> exactPlan = bigResult.plan();
        // CRAFT_LESSはAE2固有の部分成功探索を持つため、ACOが近似した結果へ置き換えない。
        if (!exactPlan.craftable() && strategy == CalculationStrategy.CRAFT_LESS) {
            return null;
        }

        Map<IPatternDetails, BigInteger> exactPatternTimes = new LinkedHashMap<>();
        // fingerprint IDを同じ世代Snapshot内の実Patternへ一対一で戻す。
        for (Map.Entry<String, BigInteger> entry : exactPlan.patternExecutions().entrySet()) {
            IPatternDetails details = graphSnapshot.pattern(entry.getKey());
            // 欠損Patternまたは0回以下は永続親Jobへ載せない。
            if (details == null || entry.getValue().signum() <= 0) {
                return null;
            }
            exactPatternTimes.merge(details, entry.getValue(), BigInteger::add);
        }
        // 画面同期と保存のPattern種類数を既存の設定上限内へ保つ。
        if (exactPatternTimes.size() > ACOConfig.getCraftingEngineShadowMaximumPatterns()) {
            return null;
        }

        BigInteger requested = BigInteger.valueOf(requestedAmount);
        BigInteger exactBytes = topology.calculateBigExactBytes(
                output,
                requested,
                exactPlan.patternExecutions(),
                ACOConfig.getBigIntegerMaximumBits());
        Ae2BigCraftingPlanFactory.PreparedBigRootPlan prepared =
                Ae2BigCraftingPlanFactory.prepareCompiledRoot(
                        output,
                        requested,
                        exactPlan,
                        exactBytes,
                        program,
                        capture.patternGeneration(),
                        capture.recipeGeneration(),
                        ACOConfig.getBigIntegerMaximumBits());
        // 一回分すらAE2互換Windowへ写せない計画は提出可能Planとして返さない。
        if (prepared == null) {
            return null;
        }
        BigIntegerCraftingPlan metadata = new BigIntegerCraftingPlan(
                new GenericStack(output, requestedAmount),
                exactPlan,
                exactPatternTimes,
                prepared);
        // AE2と周辺アドオンへは必ず最終実装CraftingPlanを返し、BigInteger真値はSidecarへ置く。
        return Ae2CraftingPlanSidecars.expose(metadata);
    }

    @Nullable
    private static ICraftingPlan createLongFacadePlan(
            Capture capture,
            Ae2CompiledCraftingGraphCache.Snapshot graphSnapshot,
            Ae2StrictCraftingTopology topology,
            AEKey output,
            long requestedAmount,
            CalculationStrategy strategy,
            NormalizedPlan symbolic) {
        // CRAFT_LESSの部分成功探索はAE2へ任せ、近似した出力量を返さない。
        if (!symbolic.craftable() && strategy == CalculationStrategy.CRAFT_LESS) {
            return null;
        }

        Map<IPatternDetails, Long> patternTimes = new LinkedHashMap<>();
        // fingerprint IDを同じ世代Snapshotの実IPatternDetailsへ戻す。
        for (Map.Entry<String, Long> entry : symbolic.patternExecutions().entrySet()) {
            IPatternDetails details = graphSnapshot.pattern(entry.getKey());
            // Pattern参照欠落または0以下の実行回数は破損計画なので採用しない。
            if (details == null || entry.getValue() <= 0L) {
                return null;
            }
            patternTimes.merge(details, entry.getValue(), Math::addExact);
        }
        // 設定した計画サイズを超える結果は同期・保存負荷を避けてAE2へ戻す。
        if (patternTimes.size() > ACOConfig.getCraftingEngineShadowMaximumPatterns()) {
            return null;
        }

        BigInteger exactBytes = topology.calculateBigExactBytes(
                output,
                BigInteger.valueOf(requestedAmount),
                symbolic.bigPatternExecutions(),
                ACOConfig.getBigIntegerMaximumBits());
        // 容量合計だけがlongを超える場合、個別カウンタはlongのままAQE Sidecarへ真値を渡す。
        if (exactBytes.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            // AQE BigInteger host連携が無効なら、標準AE2 CPUへ巨大容量を偽装しない。
            if (!ACOConfig.enableAtomicBigCapacityPlans()) {
                return null;
            }
            BigCapacityCraftingPlan metadata = new BigCapacityCraftingPlan(
                    new GenericStack(output, requestedAmount),
                    !symbolic.craftable(),
                    false,
                    keyCounter(symbolic.usedInventory()),
                    keyCounter(symbolic.emitted()),
                    keyCounter(symbolic.missing()),
                    Map.copyOf(patternTimes),
                    exactBytes,
                    capture.patternGeneration(),
                    capture.recipeGeneration());
            // 容量だけlong超過する場合も、外部へ独自ICraftingPlan実装を露出しない。
            return Ae2CraftingPlanSidecars.expose(metadata);
        }

        boolean wideInputAggregate = symbolic.hasAggregatePastLong();
        // 全集計もlong内なら、通常計画の置換はAuthoritative設定ON時だけ有効にする。
        if (!wideInputAggregate && !ACOConfig.enableAuthoritativeCompiledPlanner()) {
            return null;
        }
        // 個別値はlongでも総入力がlongを超える計画は、Atomic設定OFFならAE2へ戻す。
        if (wideInputAggregate && !ACOConfig.enableAtomicBigCapacityPlans()) {
            return null;
        }
        return new CraftingPlan(
                new GenericStack(output, requestedAmount),
                exactBytes.longValueExact(),
                !symbolic.craftable(),
                false,
                keyCounter(symbolic.usedInventory()),
                keyCounter(symbolic.emitted()),
                keyCounter(symbolic.missing()),
                Map.copyOf(patternTimes));
    }

    private static boolean planningEnabled() {
        return ACOConfig.enableAuthoritativeCompiledPlanner()
                || ACOConfig.enableAtomicBigCapacityPlans();
    }

    @Nullable
    private static NormalizedPlan normalize(
            OverflowPromotingCraftingPlanner.Result<AEKey> promoted) {
        try {
            // long高速経路は容量式用のPattern回数だけBigIntegerへ無損失変換する。
            if (promoted instanceof OverflowPromotingCraftingPlanner.LongResult<AEKey> result) {
                LongCraftingPlan<AEKey> plan = result.plan();
                return new NormalizedPlan(
                        plan.patternExecutions(),
                        bigPatternCounter(plan.patternExecutions()),
                        plan.usedInventory(),
                        plan.emitted(),
                        plan.missing());
            }
            // overflow昇格後も、AE2へ渡す全個別値がlongへ正確に戻せる場合だけ採用する。
            if (promoted instanceof OverflowPromotingCraftingPlanner.BigResult<AEKey> result) {
                BigCraftingPlan<AEKey> plan = result.plan();
                return new NormalizedPlan(
                        exactLongCounter(plan.patternExecutions()),
                        plan.patternExecutions(),
                        exactLongCounter(plan.usedInventory()),
                        exactLongCounter(plan.emitted()),
                        exactLongCounter(plan.missing()));
            }
            return null;
        } catch (ArithmeticException invalidLongBoundary) {
            return null;
        }
    }

    private static <K> Map<K, Long> exactLongCounter(Map<K, BigInteger> counts) {
        Map<K, Long> result = new LinkedHashMap<>();
        counts.forEach((key, amount) -> result.put(key, amount.longValueExact()));
        return Map.copyOf(result);
    }

    private static Map<String, BigInteger> bigPatternCounter(Map<String, Long> counts) {
        Map<String, BigInteger> result = new LinkedHashMap<>();
        counts.forEach((key, amount) -> result.put(key, BigInteger.valueOf(amount)));
        return Map.copyOf(result);
    }

    private static void logFallbackOnce(AEKey output, Throwable failure) {
        String key = output.getId() + ":" + failure.getClass().getName();
        // 同じ出力と例外型のFallback理由は一度だけdebugログへ残す。
        if (LOGGED_FALLBACKS.add(key)) {
            AE2CraftingOptimizer.LOGGER.debug(
                    "ACO authoritative planner fell back to AE2 for {}: {}",
                    output.getId(),
                    failure.toString());
        }
    }

    private static KeyCounter keyCounter(Map<AEKey, Long> counts) {
        KeyCounter result = new KeyCounter();
        counts.forEach((key, amount) -> {
            // AE2計画のKeyCounterへ0以下の量を渡さない。
            if (amount <= 0L) {
                throw new IllegalArgumentException("crafting plan counters must be positive");
            }
            result.add(key, amount);
        });
        return result;
    }

    public record Capture(
            Level level,
            IGrid grid,
            IActionSource source,
            KeyCounter inventorySnapshot,
            long patternGeneration,
            long recipeGeneration) {
        public Capture {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(grid, "grid");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(inventorySnapshot, "inventorySnapshot");
            // 負の世代値はSnapshot識別へ使用できないため拒否する。
            if (patternGeneration < 0L || recipeGeneration < 0L) {
                throw new IllegalArgumentException("generation values must not be negative");
            }
        }

        private void requireCurrentGenerations() {
            long currentPattern = ProviderPatternGenerationTracker.generation();
            long currentRecipe = RecipeGenerationTracker.generation();
            // Providerまたはrecipe世代が変わった計算結果は古いため破棄する。
            if (currentPattern != patternGeneration || currentRecipe != recipeGeneration) {
                throw new StalePlanningSnapshotException(
                        new PlanningGenerationSnapshot(patternGeneration, 0L, recipeGeneration),
                        0);
            }
        }
    }

    private record NormalizedPlan(
            Map<String, Long> patternExecutions,
            Map<String, BigInteger> bigPatternExecutions,
            Map<AEKey, Long> usedInventory,
            Map<AEKey, Long> emitted,
            Map<AEKey, Long> missing) {
        private NormalizedPlan {
            patternExecutions = Map.copyOf(patternExecutions);
            bigPatternExecutions = Map.copyOf(bigPatternExecutions);
            usedInventory = Map.copyOf(usedInventory);
            emitted = Map.copyOf(emitted);
            missing = Map.copyOf(missing);
        }

        private boolean craftable() {
            return missing.isEmpty();
        }

        private boolean hasAggregatePastLong() {
            return CheckedLongMath.sumExceedsLong(
                            patternExecutions,
                            "authoritative/pattern-total")
                    || CheckedLongMath.sumExceedsLong(
                            List.of(usedInventory, emitted, missing),
                            "authoritative/input-total");
        }
    }
}

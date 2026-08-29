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
import com.syaru.ae2craftingoptimizer.integration.PlanningExactInventorySnapshot;
import com.syaru.ae2craftingoptimizer.optimization.BigIntegerPlanDeclineReason;
import com.syaru.ae2craftingoptimizer.optimization.BigIntegerPlanDiagnostics;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
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
    /** Snapshot診断の重複除去表を固定長に保つ上限。 */
    private static final int MAXIMUM_LOGGED_SNAPSHOT_FALLBACKS = 4096;
    private static final Set<String> LOGGED_SNAPSHOT_FALLBACKS = ConcurrentHashMap.newKeySet();

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
        /*
         * 出力キーを受け取らない互換入口でも、CraftingCalculation生成側から
         * 全mountを走査しない。wide判定とexact取得はtryPlan側で行う。
         */
        return new Capture(
                level,
                grid,
                source,
                networkSnapshot,
                null,
                ArithmeticCaptureMode.UNASSESSED,
                ProviderPatternGenerationTracker.generation(),
                RecipeGenerationTracker.generation());
    }

    /**
     * CraftingCalculationの生成時にだけ使うCapture。
     * 生成側ではexact在庫を走査せず、warm cacheの証明だけを参照する。
     * cold pathは非同期Plannerで構造を判定し、wide確定時だけexact在庫を取得する。
     */
    @Nullable
    public static Capture capture(
            Level level,
            IGrid grid,
            IActionSource source,
            KeyCounter networkSnapshot,
            @Nullable AEKey output,
            long requestedAmount) {
        // 高速経路OFF、必要参照欠落、不正注文はAE2標準計算だけを使う。
        if (!planningEnabled()
                || level == null
                || grid == null
                || source == null
                || networkSnapshot == null
                || output == null
                || requestedAmount <= 0L) {
            return null;
        }

        long patternGeneration = ProviderPatternGenerationTracker.generation();
        long recipeGeneration = RecipeGenerationTracker.generation();
        try {
            boolean longSafe = hasCachedLongSafetyCertificate(
                    level,
                    grid,
                    output,
                    requestedAmount,
                    patternGeneration,
                    recipeGeneration);
            // 証明の取得中に世代が変わった場合は、その証明を新世代へ持ち越さない。
            if (longSafe
                    && patternGeneration == ProviderPatternGenerationTracker.generation()
                    && recipeGeneration == RecipeGenerationTracker.generation()) {
                return new Capture(
                        level,
                        grid,
                        source,
                        networkSnapshot,
                        null,
                        ArithmeticCaptureMode.PROVEN_LONG_SAFE,
                        patternGeneration,
                        recipeGeneration);
            }
        } catch (RuntimeException failure) {
            BigIntegerPlanDiagnostics.record(
                    BigIntegerPlanDeclineReason.INTERNAL_FAILURE,
                    output.getId().toString(),
                    BigInteger.valueOf(requestedAmount),
                    patternGeneration,
                    recipeGeneration,
                    "cached long-safety lookup failed: " + failure.getClass().getName());
        }
        // cold pathと証明不能な注文は在庫を読まず、非同期側の構造判定へ渡す。
        return new Capture(
                level,
                grid,
                source,
                networkSnapshot,
                null,
                ArithmeticCaptureMode.UNASSESSED,
                ProviderPatternGenerationTracker.generation(),
                RecipeGenerationTracker.generation());
    }

    private static boolean hasCachedLongSafetyCertificate(
            Level level,
            IGrid grid,
            AEKey output,
            long requestedAmount,
            long patternGeneration,
            long recipeGeneration) {
        Ae2CompiledCraftingGraphCache.Snapshot graphSnapshot =
                Ae2CompiledCraftingGraphCache.currentSnapshot(
                                grid,
                                level,
                                patternGeneration,
                                recipeGeneration)
                        .orElse(null);
        // cold cacheではメインスレッドからグラフを構築せず、非同期側の構造判定へ渡す。
        if (graphSnapshot == null) {
            return false;
        }
        CompiledRootProgram.Outcome<AEKey> outcome =
                graphSnapshot.cachedRootProgramOutcome(output).orElse(null);
        // Root Program未構築または構造的Fallback済みなら、安全証明を新規計算しない。
        if (outcome == null || outcome.program().isEmpty()) {
            return false;
        }
        Ae2StrictCraftingTopology topology =
                graphSnapshot.cachedStrictTopology(output).orElse(null);
        // Topology未検証時も、Pattern API走査をCraftingCalculation生成側で開始しない。
        if (topology == null) {
            return false;
        }
        return topology.cachedLongSafetyCertificate(
                        BigInteger.valueOf(requestedAmount),
                        ACOConfig.getBigIntegerMaximumBits())
                .orElse(false);
    }

    @Nullable
    public static ICraftingPlan tryPlan(
            @Nullable Capture capture,
            AEKey output,
            long requestedAmount,
            CalculationStrategy strategy) {
        return tryPlanAttempt(
                capture,
                output,
                requestedAmount,
                strategy,
                true,
                false);
    }

    @Nullable
    private static ICraftingPlan tryPlanAttempt(
            @Nullable Capture capture,
            AEKey output,
            long requestedAmount,
            CalculationStrategy strategy,
            boolean staleSnapshotRetryAvailable,
            boolean priorAttemptRequiredWideArithmetic) {
        // 無効設定、参照欠落、不正注文はACO計画を作らず呼出側へ戻す。
        if (!planningEnabled()
                || capture == null
                || output == null
                || strategy == null
                || requestedAmount <= 0L) {
            BigIntegerPlanDiagnostics.record(
                    requestedAmount <= 0L
                            ? BigIntegerPlanDeclineReason.INVALID_REQUEST
                            : BigIntegerPlanDeclineReason.DISABLED,
                    output == null ? null : output.getId().toString(),
                    BigInteger.valueOf(requestedAmount),
                    capture == null ? -1L : capture.patternGeneration(),
                    capture == null ? -1L : capture.recipeGeneration(),
                    "invalid planner input");
            return null;
        }
        // 割込みはAE2標準long計算へ戻す理由ではなく、明示的な再試行可能キャンセルとする。
        if (Thread.currentThread().isInterrupted()) {
            recordDecline(
                    capture,
                    output,
                    requestedAmount,
                    BigIntegerPlanDeclineReason.CANCELLED,
                    "planner thread was interrupted before planning");
            throw new PlanningCancelledException(0);
        }

        boolean longSafetyCertified =
                capture.arithmeticCaptureMode() == ArithmeticCaptureMode.PROVEN_LONG_SAFE;
        // 通常long計画の厳密な採用経路を両方切った場合は、AE2へ返す。
        if (longSafetyCertified && !normalLongReplacementEnabled()) {
            return null;
        }

        boolean wideArithmeticRequired = priorAttemptRequiredWideArithmetic;
        try {
            capture.requireCurrentGenerations();
            Ae2CompiledCraftingGraphCache.Snapshot graphSnapshot =
                    Ae2CompiledCraftingGraphCache.getOrCompile(capture.grid(), capture.level());
            CompiledRootProgram.Outcome<AEKey> rootOutcome =
                    graphSnapshot.rootProgramOutcome(output);
            /*
             * 回帰防止: ACO Issue #103。
             * Snapshot由来の失敗だけを、同じ注文中に一度だけ再構築して再確認する。
             * 構造的な失敗は再試行せず、容量不足へも読み替えない。
             */
            if (shouldRetryRootProgram(
                    rootOutcome.failure(),
                    ACOConfig.retryIncompleteCraftingGraphSnapshot())) {
                graphSnapshot = Ae2CompiledCraftingGraphCache.recompile(
                        capture.grid(),
                        capture.level(),
                        graphSnapshot);
                rootOutcome = graphSnapshot.rootProgramOutcome(output);
            }
            var optionalProgram = rootOutcome.program();
            // 構造上コンパイル不能なルートは、理由を保持してAE2へ戻す。
            if (optionalProgram.isEmpty()) {
                BigIntegerPlanDeclineReason reason = classifyRootProgramFailure(rootOutcome.failure());
                logRootProgramFailureOnce(output, rootOutcome.failure(), capture);
                return declineOrThrow(
                        capture,
                        output,
                        requestedAmount,
                        wideArithmeticRequired,
                        reason,
                        "root program unavailable: " + rootOutcome.failure());
            }
            CompiledRootProgram<AEKey> program = optionalProgram.get();
            Ae2StrictCraftingTopology topology = graphSnapshot
                    .strictTopology(capture.level(), capture.grid(), program)
                    .orElse(null);
            // 実AE2 Pattern API上の完全一致を証明できない場合はAE2へ戻す。
            if (topology == null || !topology.acceptsInventory(capture.inventorySnapshot())) {
                return declineOrThrow(
                        capture,
                        output,
                        requestedAmount,
                        wideArithmeticRequired,
                        BigIntegerPlanDeclineReason.UNSUPPORTED_TOPOLOGY,
                        "strict topology was not proven");
            }
            // cold pathまたは保守的上界で未確定の注文だけ、正確なBigInteger preflightを行う。
            if (!longSafetyCertified) {
                wideArithmeticRequired = wideArithmeticRequired
                        || topology.mightRequireWideArithmetic(
                                output,
                                BigInteger.valueOf(requestedAmount),
                                ACOConfig.getBigIntegerMaximumBits());
            }
            boolean shadowQualified = CompiledRootQualificationRegistry.isQualified(
                    program,
                    ACOConfig.getAuthoritativeMinimumShadowMatches());
            /*
             * Shadow一致、または現在世代の厳密Topology証明のどちらも無い計画は採用しない。
             * wide計画はAE2自身が比較計算を完走できないため、既存の専用設定を優先する。
             */
            if (!isQualifiedForReplacement(
                    shadowQualified,
                    ACOConfig.enableProofQualifiedLongPlans(),
                    wideArithmeticRequired,
                    ACOConfig.requireWidePlanShadowQualification())) {
                return declineOrThrow(
                        capture,
                        output,
                        requestedAmount,
                        wideArithmeticRequired,
                        BigIntegerPlanDeclineReason.SHADOW_NOT_QUALIFIED,
                        "shadow qualification is below the configured threshold");
            }
            // 通常注文の置換を両方の設定で無効化した場合は、wide互換経路だけを残す。
            if (!normalLongReplacementEnabled() && !wideArithmeticRequired) {
                return null;
            }

            // 正確にwideと判明した注文だけ、サーバースレッドでexact在庫を固定する。
            if (capture.arithmeticCaptureMode()
                    .requiresDeferredExactInventory(wideArithmeticRequired)) {
                capture = capture.withExactInventorySnapshot(
                        captureExactInventorySnapshot(capture));
            }

            BigKeyCounterSidecars.Snapshot inventoryMetadata =
                    capture.exactInventorySnapshot() == null
                            ? null
                            : BigKeyCounterSidecars.snapshot(
                                    capture.exactInventorySnapshot()).orElse(null);
            CompiledRootProgram.BigInventorySnapshot<AEKey> exactPlanningInventory =
                    capture.exactInventorySnapshot() == null
                            ? null
                            : Ae2ReferencedInventory.captureExactNetworkSnapshot(
                                    program,
                                    capture.exactInventorySnapshot(),
                                    output);
            CompiledRootProgram.InventorySnapshot<AEKey> planningInventory = null;
            // 参照キーを個別に証明できない場合だけ、通常AE2のlong Snapshotへ戻す。
            if (exactPlanningInventory == null) {
                // 不完全Sidecarを飽和longとしてBig計画へ渡すと、在庫不足を誤って充足扱いにする。
                if (inventoryMetadata != null && !inventoryMetadata.complete()) {
                    return declineOrThrow(
                            capture,
                            output,
                            requestedAmount,
                            wideArithmeticRequired,
                            BigIntegerPlanDeclineReason.INCOMPLETE_INVENTORY,
                            "BigInteger inventory sidecar is incomplete");
                }
                planningInventory = Ae2ReferencedInventory.captureNetworkSnapshot(
                        program,
                        capture.inventorySnapshot(),
                        output);
            }
            Capture guardCapture = capture;
            PlanningGuard guard = expanded -> {
                // 64ノードごとに世代変更とスレッド割込みを確認し、古い結果を早めに破棄する。
                if ((expanded & GENERATION_CHECK_INTERVAL_MASK) == 0) {
                    guardCapture.requireCurrentGenerations();
                    // 計算キャンセル後は残りノードを処理しない。
                    if (Thread.currentThread().isInterrupted()) {
                        throw new PlanningCancelledException(expanded);
                    }
                }
            };
            OverflowPromotingCraftingPlanner<AEKey> planner =
                    new OverflowPromotingCraftingPlanner<>(
                            ACOConfig.getBigIntegerMaximumBits());
            final CompiledRootProgram.BigInventorySnapshot<AEKey> exactInventorySnapshot = exactPlanningInventory;
            final CompiledRootProgram.InventorySnapshot<AEKey> longInventorySnapshot = planningInventory;
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
            // 実際にlong計算からBigへ昇格した場合、exact在庫なしの結果は正本にならない。
            if (promoted.usesBigInteger() && exactPlanningInventory == null) {
                return declineOrThrow(
                        capture,
                        output,
                        requestedAmount,
                        true,
                        BigIntegerPlanDeclineReason.INCOMPLETE_INVENTORY,
                        "long plan promoted without an exact inventory snapshot");
            }
            // Root Program経路以外の結果はAuthoritativeとして採用しない。
            if (!promoted.provenEquivalent()) {
                return declineOrThrow(
                        capture,
                        output,
                        requestedAmount,
                        wideArithmeticRequired || promoted.usesBigInteger(),
                        BigIntegerPlanDeclineReason.PLAN_NOT_PROVEN,
                        "compiled plan was not proven equivalent");
            }
            NormalizedPlan symbolic = normalize(promoted);
            ICraftingPlan result;
            BigCraftingPlan<AEKey> exactPlan = exactPlan(promoted);
            boolean widePlan = wideArithmeticRequired
                    || promoted.usesBigInteger()
                    || symbolic == null;
            // wide不足はAE2のlong計算へ戻さず、正確なsimulationまたは部分探索へ進める。
            if (!exactPlan.craftable() && widePlan) {
                if (strategy == CalculationStrategy.CRAFT_LESS) {
                    BigInteger partialAmount = largestCraftableAmount(
                            BigInteger.valueOf(requestedAmount),
                            candidate -> exactPlanAt(
                                    planner,
                                    program,
                                    candidate,
                                    exactInventorySnapshot,
                                    longInventorySnapshot,
                                    guard));
                    if (partialAmount.signum() <= 0) {
                        result = createBigIntegerSimulationPlan(
                                graphSnapshot,
                                topology,
                                output,
                                BigInteger.valueOf(requestedAmount),
                                exactPlan);
                    } else {
                        OverflowPromotingCraftingPlanner.Result<AEKey> partial = exactPlanAt(
                                planner,
                                program,
                                partialAmount,
                                exactInventorySnapshot,
                                longInventorySnapshot,
                                guard);
                        result = createBigIntegerParentPlan(
                                capture,
                                graphSnapshot,
                                program,
                                topology,
                                output,
                                partialAmount,
                                strategy,
                                partial,
                                true);
                    }
                } else {
                    result = createBigIntegerSimulationPlan(
                            graphSnapshot,
                            topology,
                            output,
                            BigInteger.valueOf(requestedAmount),
                            exactPlan);
                }
                if (result == null) {
                    return declineOrThrow(
                            capture,
                            output,
                            requestedAmount,
                            true,
                            BigIntegerPlanDeclineReason.MISSING_SIMULATION,
                            "exact wide missing simulation could not be represented");
                }
            } else {
                // 個別値またはキー別合計がlongを超える場合、通常AE2 Jobへ戻さずBig親Jobを作る。
                boolean bigIntegerExecutionRequired = symbolic == null
                        || symbolic.hasAggregatePastLong();
                if (bigIntegerExecutionRequired) {
                    result = createBigIntegerParentPlan(
                            capture,
                            graphSnapshot,
                            program,
                            topology,
                            output,
                            BigInteger.valueOf(requestedAmount),
                            strategy,
                            promoted,
                            true);
                    // 厳格な親Jobへ変換できない経路は、値を近似せずAE2本来の計算へ戻す。
                    if (result == null) {
                        return declineOrThrow(
                                capture,
                                output,
                                requestedAmount,
                                widePlan,
                                BigIntegerPlanDeclineReason.EXECUTION_WINDOW_UNAVAILABLE,
                                "exact execution plan could not be prepared");
                    }
                } else {
                    result = createLongFacadePlan(
                            capture,
                            graphSnapshot,
                            topology,
                            output,
                            requestedAmount,
                            strategy,
                            symbolic,
                            wideArithmeticRequired);
                    // AtomicまたはAuthoritative設定で採用できない通常計画はAE2へ戻す。
                    if (result == null) {
                        return declineOrThrow(
                                capture,
                                output,
                                requestedAmount,
                                widePlan,
                                BigIntegerPlanDeclineReason.UNSUPPORTED_TOPOLOGY,
                                "long facade could not be built");
                    }
                }
            }

            capture.requireCurrentGenerations();
            KeyCounter currentExactInventory = null;
            // wide計画の再検証も全mountへ触れるため、計算workerから直接走査しない。
            if (exactPlanningInventory != null) {
                currentExactInventory = captureExactInventorySnapshot(capture);
                // server executorとの往復中にPattern世代が変わった結果は採用しない。
                capture.requireCurrentGenerations();
            }
            // 計算で参照したキーだけでも在庫が変わっていれば、古い結果を返さない。
            boolean inventoryStillMatches = exactPlanningInventory != null
                    ? Ae2ReferencedInventory.matchesLive(
                            program,
                            exactPlanningInventory,
                            capture.grid(),
                            capture.source(),
                            output,
                            currentExactInventory)
                    : Ae2ReferencedInventory.matchesLive(
                            program,
                            planningInventory,
                            capture.grid(),
                            capture.source(),
                            output);
            if (!inventoryStillMatches) {
                return declineOrThrow(
                        capture,
                        output,
                        requestedAmount,
                        widePlan,
                        BigIntegerPlanDeclineReason.INVENTORY_CHANGED,
                        "referenced inventory changed during planning");
            }
            // Emitterまたはファジー候補が変わった場合も、AE2と選択結果がずれるため破棄する。
            if (!topology.remainsValid(capture.grid())) {
                return declineOrThrow(
                        capture,
                        output,
                        requestedAmount,
                        widePlan,
                        BigIntegerPlanDeclineReason.TOPOLOGY_CHANGED,
                        "strict topology changed during planning");
            }
            return result;
        } catch (PlanningCancelledException cancelled) {
            recordDecline(
                    capture,
                    output,
                    requestedAmount,
                    BigIntegerPlanDeclineReason.CANCELLED,
                    cancelled.getMessage());
            throw cancelled;
        } catch (StalePlanningSnapshotException stale) {
            StaleSnapshotAction action = staleSnapshotAction(
                    staleSnapshotRetryAvailable,
                    Thread.currentThread().isInterrupted(),
                    wideArithmeticRequired,
                    capture.canRefreshGenerations());
            // AE2が計算をキャンセル済みなら、新しいSnapshotでも計算を再開しない。
            if (action == StaleSnapshotAction.CANCEL) {
                recordDecline(
                        capture,
                        output,
                        requestedAmount,
                        BigIntegerPlanDeclineReason.CANCELLED,
                        stale.getMessage() + "; recovery=" + action);
                throw new PlanningCancelledException(stale.expandedRequests());
            }
            // 初回の世代競合だけ、同じAE2在庫Snapshotを最新世代へ結び直して再計算する。
            if (action == StaleSnapshotAction.RETRY) {
                return tryPlanAttempt(
                        capture.refreshGenerations(),
                        output,
                        requestedAmount,
                        strategy,
                        false,
                        false);
            }
            recordDecline(
                    capture,
                    output,
                    requestedAmount,
                    BigIntegerPlanDeclineReason.GENERATION_CHANGED,
                    stale.getMessage() + "; recovery=" + action);
            // 通常long計画は入力を動かす前に辞退し、呼出元のAE2標準計算へ戻す。
            if (action == StaleSnapshotAction.FALLBACK_TO_AE2) {
                return null;
            }
            throw new WidePlanUnavailableException(
                    output,
                    "wide planning snapshot changed repeatedly",
                    stale);
        } catch (ArithmeticException arithmeticFailure) {
            recordDecline(
                    capture,
                    output,
                    requestedAmount,
                    BigIntegerPlanDeclineReason.ARITHMETIC_FAILURE,
                    arithmeticFailure.getMessage());
            if (wideArithmeticRequired) {
                throw new WidePlanUnavailableException(output, "exact arithmetic failed", arithmeticFailure);
            }
            return null;
        } catch (WidePlanUnavailableException unavailable) {
            // wide計画はAE2標準long経路へ戻さず、元の明示的な辞退理由を保持する。
            throw unavailable;
        } catch (RuntimeException failure) {
            recordDecline(
                    capture,
                    output,
                    requestedAmount,
                    BigIntegerPlanDeclineReason.INTERNAL_FAILURE,
                    failure.getClass().getName());
            if (wideArithmeticRequired) {
                throw new WidePlanUnavailableException(output, "wide plan failed", failure);
            }
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
            BigInteger requestedAmount,
            CalculationStrategy strategy,
            OverflowPromotingCraftingPlanner.Result<AEKey> promoted,
            boolean requiresBigIntegerExecution) {
        if (!ACOConfig.enableBigIntegerGameplayExecution()) {
            return null;
        }
        BigCraftingPlan<AEKey> exactPlan;
        if (promoted instanceof OverflowPromotingCraftingPlanner.BigResult<AEKey> bigResult) {
            // 途中の掛け算がoverflowした場合は、Plannerが最初から作ったBigInteger結果を使う。
            exactPlan = bigResult.plan();
        } else if (promoted instanceof OverflowPromotingCraftingPlanner.LongResult<AEKey> longResult) {
            // 個別値はlong内でも合計だけが超過する場合は、long結果を無損失でBigIntegerへ昇格する。
            exactPlan = widenLongPlan(longResult.plan());
        } else {
            return null;
        }
        // CRAFT_LESSはAE2固有の部分成功探索を持つため、ACOが近似した結果へ置き換えない。
        if (!exactPlan.craftable() && strategy == CalculationStrategy.CRAFT_LESS) {
            return null;
        }

        Map<IPatternDetails, BigInteger> exactPatternTimes = resolveExactPatternTimes(
                graphSnapshot,
                exactPlan.patternExecutions());
        // Pattern参照を同一世代へ戻せない計画は、永続親Jobとして採用しない。
        if (exactPatternTimes == null) {
            return null;
        }

        BigInteger requested = requestedAmount;
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
        // 防御的にnullを扱う。現在のFactoryはExact専用計画も保持するため通常はnullにならない。
        if (prepared == null) {
            return null;
        }
        BigIntegerCraftingPlan metadata = new BigIntegerCraftingPlan(
                new GenericStack(output, requested.longValueExact()),
                exactPlan,
                exactPatternTimes,
                prepared,
                requiresBigIntegerExecution);
        // AE2と周辺アドオンへは必ず最終実装CraftingPlanを返し、BigInteger真値はSidecarへ置く。
        return Ae2CraftingPlanSidecars.expose(metadata);
    }

    private static BigCraftingPlan<AEKey> exactPlan(
            OverflowPromotingCraftingPlanner.Result<AEKey> promoted) {
        if (promoted instanceof OverflowPromotingCraftingPlanner.BigResult<AEKey> bigResult) {
            return bigResult.plan();
        }
        if (promoted instanceof OverflowPromotingCraftingPlanner.LongResult<AEKey> longResult) {
            return widenLongPlan(longResult.plan());
        }
        throw new IllegalArgumentException("Unsupported promoted crafting result");
    }

    private static OverflowPromotingCraftingPlanner.Result<AEKey> exactPlanAt(
            OverflowPromotingCraftingPlanner<AEKey> planner,
            CompiledRootProgram<AEKey> program,
            BigInteger amount,
            CompiledRootProgram.BigInventorySnapshot<AEKey> exactInventory,
            CompiledRootProgram.InventorySnapshot<AEKey> longInventory,
            PlanningGuard guard) {
        // 同じ不変在庫Snapshotで候補量だけを変えてBigInteger計画を再計算する。
        if (exactInventory != null) {
            return planner.plan(program, amount, exactInventory, guard);
        }
        return planner.plan(program, amount, longInventory, guard);
    }

    private static BigInteger largestCraftableAmount(
            BigInteger requestedAmount,
            Function<BigInteger, OverflowPromotingCraftingPlanner.Result<AEKey>> planner) {
        OverflowPromotingCraftingPlanner.Result<AEKey> full = planner.apply(requestedAmount);
        // 要求全量が成立する場合は二分探索を省略する。
        if (full.craftable()) {
            return requestedAmount;
        }

        BigInteger lower = BigInteger.ZERO;
        BigInteger upper = requestedAmount;
        // 成立量と不成立量の境界をBigIntegerの二分探索で求める。
        while (lower.add(BigInteger.ONE).compareTo(upper) < 0) {
            BigInteger middle = lower.add(upper).shiftRight(1);
            OverflowPromotingCraftingPlanner.Result<AEKey> candidate = planner.apply(middle);
            // 候補量が成立するかを判定し、探索区間を半分へ狭める。
            if (candidate.craftable()) {
                lower = middle;
                continue;
            }
            upper = middle;
        }
        // lowerはBigInteger上で証明できた最大作成量である。
        return lower;
    }

    private static ICraftingPlan createBigIntegerSimulationPlan(
            Ae2CompiledCraftingGraphCache.Snapshot graphSnapshot,
            Ae2StrictCraftingTopology topology,
            AEKey output,
            BigInteger requestedAmount,
            BigCraftingPlan<AEKey> exactPlan) {
        Map<IPatternDetails, BigInteger> exactPatternTimes = resolveExactPatternTimes(
                graphSnapshot,
                exactPlan.patternExecutions());
        // 不足simulationも実行計画と同じPattern参照条件を満たす必要がある。
        if (exactPatternTimes == null) {
            return null;
        }
        BigInteger exactBytes = topology.calculateBigExactBytes(
                output,
                requestedAmount,
                exactPlan.patternExecutions(),
                ACOConfig.getBigIntegerMaximumBits());
        return Ae2CraftingPlanSidecars.expose(
                new BigIntegerSimulationPlan(
                        new GenericStack(output, requestedAmount.longValueExact()),
                        exactPlan,
                        exactPatternTimes,
                        exactBytes));
    }

    @Nullable
    private static Map<IPatternDetails, BigInteger> resolveExactPatternTimes(
            Ae2CompiledCraftingGraphCache.Snapshot graphSnapshot,
            Map<String, BigInteger> fingerprintCounts) {
        Map<IPatternDetails, BigInteger> resolved = new LinkedHashMap<>();
        // fingerprint IDを同じ世代Snapshot内の実Patternへ一対一で戻す。
        for (Map.Entry<String, BigInteger> entry : fingerprintCounts.entrySet()) {
            IPatternDetails details = graphSnapshot.pattern(entry.getKey());
            // 欠損Patternまたは0回以下は表示にも永続Jobにも載せない。
            if (details == null || entry.getValue().signum() <= 0) {
                return null;
            }
            resolved.merge(details, entry.getValue(), BigInteger::add);
        }
        // 画面同期と保存のPattern種類数を既存の設定上限内へ保つ。
        if (resolved.size() > ACOConfig.getCraftingEngineShadowMaximumPatterns()) {
            return null;
        }
        return Map.copyOf(resolved);
    }

    private static ICraftingPlan declineOrThrow(
            Capture capture,
            AEKey output,
            long requestedAmount,
            boolean wide,
            BigIntegerPlanDeclineReason reason,
            String detail) {
        recordDecline(capture, output, requestedAmount, reason, detail);
        if (wide) {
            throw new WidePlanUnavailableException(output, detail);
        }
        return null;
    }

    /** Snapshot由来の一時失敗だけを再構築対象にする。 */
    static boolean shouldRetryRootProgram(
            RootProgramFailure failure,
            boolean retryEnabled) {
        return retryEnabled && failure.snapshotShaped();
    }

    /** Root Programの失敗理由を公開統計の安定したコードへ変換する。 */
    static BigIntegerPlanDeclineReason classifyRootProgramFailure(RootProgramFailure failure) {
        return switch (failure) {
            case CYCLE -> BigIntegerPlanDeclineReason.CYCLE;
            case MULTIPLE_PRODUCERS -> BigIntegerPlanDeclineReason.AMBIGUOUS_PRODUCER;
            case MULTIPLE_OUTPUTS -> BigIntegerPlanDeclineReason.UNSUPPORTED_PATTERN;
            case PROGRAM_TOO_LARGE -> BigIntegerPlanDeclineReason.PROGRAM_TOO_LARGE;
            case INCOMPLETE_PATTERN_SNAPSHOT, MISSING_FROM_SNAPSHOT ->
                    BigIntegerPlanDeclineReason.INCOMPLETE_GRAPH_SNAPSHOT;
            case NONE -> BigIntegerPlanDeclineReason.NO_COMPILED_PROGRAM;
        };
    }

    /** 不完全Snapshotが再構築後も残った場合だけ、世代ごとに一度警告する。 */
    private static void logRootProgramFailureOnce(
            AEKey output,
            RootProgramFailure failure,
            Capture capture) {
        // 構造的な辞退は通常の統計へ残し、警告ログの対象にはしない。
        if (!failure.snapshotShaped() || !ACOConfig.logWidePlanSubmissionDeclines()) {
            return;
        }
        String key = output.getId()
                + ":" + failure
                + ":" + capture.patternGeneration()
                + ":" + capture.recipeGeneration();
        // 世代が進み続けても常駐量が増え続けないよう、固定上限で表を再利用する。
        if (LOGGED_SNAPSHOT_FALLBACKS.size() >= MAXIMUM_LOGGED_SNAPSHOT_FALLBACKS) {
            LOGGED_SNAPSHOT_FALLBACKS.clear();
        }
        if (!LOGGED_SNAPSHOT_FALLBACKS.add(key)) {
            return;
        }
        AE2CraftingOptimizer.LOGGER.warn(
                "ACO could not compile {} because the crafting graph snapshot remained incomplete"
                        + " after one bounded rebuild (reason={}, patternGeneration={}, recipeGeneration={}).",
                output.getId(),
                failure,
                capture.patternGeneration(),
                capture.recipeGeneration());
    }

    private static void recordDecline(
            @Nullable Capture capture,
            @Nullable AEKey output,
            long requestedAmount,
            BigIntegerPlanDeclineReason reason,
            String detail) {
        BigIntegerPlanDiagnostics.record(
                reason,
                output == null ? null : output.getId().toString(),
                BigInteger.valueOf(requestedAmount),
                capture == null ? -1L : capture.patternGeneration(),
                capture == null ? -1L : capture.recipeGeneration(),
                detail);
    }

    private static BigCraftingPlan<AEKey> widenLongPlan(LongCraftingPlan<AEKey> source) {
        Objects.requireNonNull(source, "source");
        // long値を文字列経由にせず、BigInteger.valueOfで符号反転のない昇格を行う。
        return new BigCraftingPlan<>(
                source.requestedKey(),
                BigInteger.valueOf(source.requestedAmount()),
                widenLongMap(source.patternExecutions()),
                widenLongMap(source.usedInventory()),
                widenLongMap(source.emitted()),
                widenLongMap(source.missing()),
                0);
    }

    private static <K> Map<K, BigInteger> widenLongMap(Map<K, Long> source) {
        Map<K, BigInteger> widened = new LinkedHashMap<>();
        // 既存の正確なlong会計をキーごとに一度だけBigIntegerへ写す。
        source.forEach((key, amount) -> widened.put(key, BigInteger.valueOf(amount)));
        return Map.copyOf(widened);
    }

    @Nullable
    private static ICraftingPlan createLongFacadePlan(
            Capture capture,
            Ae2CompiledCraftingGraphCache.Snapshot graphSnapshot,
            Ae2StrictCraftingTopology topology,
            AEKey output,
            long requestedAmount,
            CalculationStrategy strategy,
            NormalizedPlan symbolic,
            boolean fullExpansionRequiresWideArithmetic) {
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
        /*
         * BigIntegerセル在庫で現在の計画がlong内へ縮んでも、全展開がlongを超えるルートは
         * AE2の飽和long在庫へ戻すと別の計画になる。この互換経路は任意最適化OFFでも維持する。
         */
        if (!wideInputAggregate
                && !shouldRetainLongFacade(
                        normalLongReplacementEnabled(),
                        fullExpansionRequiresWideArithmetic)) {
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

    /**
     * 通常Authoritative最適化と、wide在庫を正しく扱うための必須互換経路を分離する。
     */
    static boolean shouldRetainLongFacade(
            boolean authoritativePlannerEnabled,
            boolean fullExpansionRequiresWideArithmetic) {
        return authoritativePlannerEnabled || fullExpansionRequiresWideArithmetic;
    }

    /**
     * 厳密Topologyが既に成立した後の採用規則。
     * wide注文だけはAE2標準計算でShadow教材を作れないため、専用設定を維持する。
     */
    static boolean isQualifiedForReplacement(
            boolean shadowQualified,
            boolean proofQualifiedLongPlansEnabled,
            boolean wideArithmeticRequired,
            boolean requireWideShadowQualification) {
        if (shadowQualified) {
            return true;
        }
        if (wideArithmeticRequired) {
            return !requireWideShadowQualification;
        }
        return proofQualifiedLongPlansEnabled;
    }

    enum StaleSnapshotAction {
        RETRY,
        FALLBACK_TO_AE2,
        REJECT_WIDE,
        CANCEL
    }

    /**
     * 世代競合時の回復方法を、通常long計画とwide計画で分離する。
     */
    static StaleSnapshotAction staleSnapshotAction(
            boolean retryAvailable,
            boolean interrupted,
            boolean wideArithmeticRequired) {
        return staleSnapshotAction(
                retryAvailable,
                interrupted,
                wideArithmeticRequired,
                true);
    }

    static StaleSnapshotAction staleSnapshotAction(
            boolean retryAvailable,
            boolean interrupted,
            boolean wideArithmeticRequired,
            boolean generationRefreshAllowed) {
        // AE2のキャンセル要求は再試行や標準計算より優先する。
        if (interrupted) {
            return StaleSnapshotAction.CANCEL;
        }
        // exact在庫を保持する一回目の競合だけ、最新Pattern世代へ結び直して再計算する。
        if (retryAvailable && generationRefreshAllowed) {
            return StaleSnapshotAction.RETRY;
        }
        // wideと判明済みの計画をoverflowするAE2標準long計算へ落とさない。
        if (wideArithmeticRequired) {
            return StaleSnapshotAction.REJECT_WIDE;
        }
        return StaleSnapshotAction.FALLBACK_TO_AE2;
    }

    private static boolean normalLongReplacementEnabled() {
        return normalLongReplacementEnabled(
                ACOConfig.enableAuthoritativeCompiledPlanner(),
                ACOConfig.enableProofQualifiedLongPlans());
    }

    static boolean normalLongReplacementEnabled(
            boolean authoritativePlannerEnabled,
            boolean proofQualifiedLongPlansEnabled) {
        // 厳密証明を通過した二つの採用経路だけが、通常long計画を置換できる。
        return authoritativePlannerEnabled || proofQualifiedLongPlansEnabled;
    }

    private static boolean planningEnabled() {
        return normalLongReplacementEnabled()
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

    private static KeyCounter captureExactInventorySnapshot(Capture capture) {
        MinecraftServer server = capture.level().getServer();
        // サーバーを持たないLevelではexact正本を安全なスレッドへ委譲できないため失敗させる。
        if (server == null) {
            throw new IllegalStateException("exact inventory capture requires a server level");
        }
        // 既にサーバースレッド上ならFutureを作らず、その場で一度だけ取得する。
        if (server.isSameThread()) {
            return PlanningExactInventorySnapshot.capture(capture.grid());
        }
        CompletableFuture<KeyCounter> pending = CompletableFuture.supplyAsync(
                () -> PlanningExactInventorySnapshot.capture(capture.grid()),
                server);
        try {
            return pending.get();
        } catch (InterruptedException interrupted) {
            pending.cancel(false);
            Thread.currentThread().interrupt();
            throw new PlanningCancelledException(0);
        } catch (ExecutionException failedCapture) {
            Throwable cause = failedCapture.getCause();
            // RuntimeExceptionは元の型と診断を維持し、別理由へ読み替えない。
            if (cause instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            // VM Errorもラップして継続せず、元の致命的状態をそのまま伝播する。
            if (cause instanceof Error fatalFailure) {
                throw fatalFailure;
            }
            throw new IllegalStateException("exact inventory capture failed", cause);
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

    public enum ArithmeticCaptureMode {
        EXACT_AVAILABLE,
        UNASSESSED,
        PROVEN_LONG_SAFE;

        boolean requiresDeferredExactInventory(boolean wideArithmeticRequired) {
            // long範囲の注文では、未評価でも全mountのexact在庫走査を開始しない。
            if (!wideArithmeticRequired) {
                return false;
            }
            // exact正本を既に持つCaptureだけは、同じ在庫を二重取得しない。
            return this != EXACT_AVAILABLE;
        }
    }

    public record Capture(
            Level level,
            IGrid grid,
            IActionSource source,
            KeyCounter inventorySnapshot,
            @Nullable KeyCounter exactInventorySnapshot,
            ArithmeticCaptureMode arithmeticCaptureMode,
            long patternGeneration,
            long recipeGeneration) {
        /** 1.5.29以前の公開コンストラクタを、正確判定が必要なCaptureとして維持する。 */
        public Capture(
                Level level,
                IGrid grid,
                IActionSource source,
                KeyCounter inventorySnapshot,
                KeyCounter exactInventorySnapshot,
                long patternGeneration,
                long recipeGeneration) {
            this(
                    level,
                    grid,
                    source,
                    inventorySnapshot,
                    exactInventorySnapshot,
                    ArithmeticCaptureMode.EXACT_AVAILABLE,
                    patternGeneration,
                    recipeGeneration);
        }

        public Capture {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(grid, "grid");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(inventorySnapshot, "inventorySnapshot");
            Objects.requireNonNull(arithmeticCaptureMode, "arithmeticCaptureMode");
            // EXACT_AVAILABLEは、後続の正確なwide計画に使うexact在庫を必須とする。
            if (arithmeticCaptureMode == ArithmeticCaptureMode.EXACT_AVAILABLE
                    && exactInventorySnapshot == null) {
                throw new IllegalArgumentException(
                        "exact capture requires an exact inventory snapshot");
            }
            // long安全証明済みCaptureへexact正本を二重保持せず、状態の意味を一意にする。
            if (arithmeticCaptureMode == ArithmeticCaptureMode.PROVEN_LONG_SAFE
                    && exactInventorySnapshot != null) {
                throw new IllegalArgumentException(
                        "proven long-safe capture must not retain an exact inventory snapshot");
            }
            // 未評価Captureはexact取得前の状態なので、二重管理を許可しない。
            if (arithmeticCaptureMode == ArithmeticCaptureMode.UNASSESSED
                    && exactInventorySnapshot != null) {
                throw new IllegalArgumentException(
                        "unassessed capture must not retain an exact inventory snapshot");
            }
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

        private Capture refreshGenerations() {
            // exact正本を持たない安全証明は、新しいPattern世代へ再利用しない。
            if (!canRefreshGenerations()) {
                throw new IllegalStateException(
                        "long-safety certificate cannot be rebound to a new generation");
            }
            return new Capture(
                    level,
                    grid,
                    source,
                    inventorySnapshot,
                    exactInventorySnapshot,
                    arithmeticCaptureMode,
                    ProviderPatternGenerationTracker.generation(),
                    RecipeGenerationTracker.generation());
        }

        private Capture withExactInventorySnapshot(KeyCounter exactSnapshot) {
            return new Capture(
                    level,
                    grid,
                    source,
                    inventorySnapshot,
                    Objects.requireNonNull(exactSnapshot, "exactSnapshot"),
                    ArithmeticCaptureMode.EXACT_AVAILABLE,
                    patternGeneration,
                    recipeGeneration);
        }

        private boolean canRefreshGenerations() {
            return arithmeticCaptureMode != ArithmeticCaptureMode.PROVEN_LONG_SAFE;
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

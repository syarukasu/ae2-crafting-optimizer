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
import com.syaru.ae2craftingoptimizer.optimization.PlanningConfigurationRevisionTracker;
import com.syaru.ae2craftingoptimizer.optimization.ServerPlanningThreadGuard;
import com.syaru.ae2craftingoptimizer.optimization.StorageRevisionTracker;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * AE2標準計画とのShadow一致実績があり、厳密に証明できるRoot Programだけを置き換えるPlanner。
 * 対応外構造は所有権取得前にnullを返す。内部不整合はAE2 fallbackへ偽装せず明示失敗する。
 */
public final class Ae2AuthoritativeCraftingPlanner {
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
                || networkSnapshot == null
                || !ServerPlanningThreadGuard.canCapture(level)) {
            return null;
        }
        /*
         * 出力キーを受け取らない互換入口でも、CraftingCalculation生成側から
         * 全mountを走査しない。wide判定とexact取得はtryPlan側で行う。
         */
        StorageRevisionTracker.RevisionToken storageRevision =
                StorageRevisionTracker.refreshAndCapture(grid);
        return new Capture(
                level,
                grid,
                source,
                Ae2PlanningInventorySnapshot.capture(networkSnapshot),
                null,
                null,
                ArithmeticCaptureMode.UNASSESSED,
                ProviderPatternGenerationTracker.generation(),
                RecipeGenerationTracker.generation(),
                storageRevision,
                PlanningConfigurationRevisionTracker.current());
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
        // live StorageServiceのrefreshはserver所有threadでだけ実行する。
        if (!ServerPlanningThreadGuard.canCapture(level)) {
            return null;
        }
        return capture(
                level,
                grid,
                source,
                networkSnapshot,
                output,
                requestedAmount,
                grid == null ? null : StorageRevisionTracker.refreshAndCapture(grid));
    }

    @Nullable
    public static Capture capture(
            Level level,
            IGrid grid,
            IActionSource source,
            KeyCounter networkSnapshot,
            @Nullable AEKey output,
            long requestedAmount,
            @Nullable StorageRevisionTracker.RevisionToken storageRevision) {
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
                requestedAmount,
                storageRevision);
    }

    @Nullable
    public static Capture capture(
            Level level,
            IGrid grid,
            IActionSource source,
            Ae2PlanningInventorySnapshot networkSnapshot,
            @Nullable AEKey output,
            long requestedAmount,
            @Nullable StorageRevisionTracker.RevisionToken storageRevision) {
        // 高速経路OFF、必要参照欠落、不正注文はAE2標準計算だけを使う。
        if (!planningEnabled()
                || level == null
                || grid == null
                || source == null
                || networkSnapshot == null
                || output == null
                || requestedAmount <= 0L
                || storageRevision == null
                || !ServerPlanningThreadGuard.canCapture(level)) {
            return null;
        }

        long patternGeneration = ProviderPatternGenerationTracker.generation();
        long recipeGeneration = RecipeGenerationTracker.generation();
        long configurationRevision = PlanningConfigurationRevisionTracker.current();
        Ae2ImmutablePlanningGraphCache.RootCapture graphCapture =
                Ae2ImmutablePlanningGraphCache.capture(grid, level, output);
        return capturePrepared(
                level,
                grid,
                source,
                networkSnapshot,
                output,
                requestedAmount,
                storageRevision,
                graphCapture,
                patternGeneration,
                recipeGeneration,
                configurationRevision);
    }

    @Nullable
    static Capture capturePrepared(
            Level level,
            IGrid grid,
            IActionSource source,
            Ae2PlanningInventorySnapshot networkSnapshot,
            AEKey output,
            long requestedAmount,
            StorageRevisionTracker.RevisionToken storageRevision,
            @Nullable Ae2ImmutablePlanningGraphCache.RootCapture graphCapture,
            long patternGeneration,
            long recipeGeneration,
            long configurationRevision) {
        // Coordinatorで固定した不変値以外をworker用Captureへ入れない。
        if (!planningEnabled()
                || level == null
                || grid == null
                || source == null
                || networkSnapshot == null
                || output == null
                || requestedAmount <= 0L
                || storageRevision == null
                || !ServerPlanningThreadGuard.canCapture(level)
                || !PlanningConfigurationRevisionTracker.isCurrent(
                        configurationRevision)) {
            return null;
        }
        // Issue #167: revision前後が一致しないPattern captureへ新世代を付け直さずAE2へ戻す。
        if (graphCapture == null
                || graphCapture.patternGeneration() != patternGeneration
                || graphCapture.recipeGeneration() != recipeGeneration
                || graphCapture.configurationRevision() != configurationRevision) {
            return null;
        }
        boolean longSafe;
        try {
            longSafe = hasCachedLongSafetyCertificate(
                    graphCapture,
                    output,
                    requestedAmount,
                    patternGeneration,
                    recipeGeneration);
        } catch (RuntimeException failure) {
            BigIntegerPlanDiagnostics.record(
                    BigIntegerPlanDeclineReason.INTERNAL_FAILURE,
                    output.getId().toString(),
                    BigInteger.valueOf(requestedAmount),
                    patternGeneration,
                    recipeGeneration,
                    "cached long-safety lookup failed: " + failure.getClass().getName());
            // Issue #167: 内部cache破損をcold pathへ偽装せず、原因を保持して明示失敗する。
            throw new IllegalStateException("ACO cached long-safety lookup failed", failure);
        }
        // 証明の取得中に世代が変わった場合は、その証明を新世代へ持ち越さない。
        if (longSafe
                && patternGeneration == ProviderPatternGenerationTracker.generation()
                && recipeGeneration == RecipeGenerationTracker.generation()
                && PlanningConfigurationRevisionTracker.isCurrent(
                        configurationRevision)) {
            return new Capture(
                    level,
                    grid,
                    source,
                    networkSnapshot,
                    null,
                    graphCapture,
                    ArithmeticCaptureMode.PROVEN_LONG_SAFE,
                    patternGeneration,
                    recipeGeneration,
                    storageRevision,
                    configurationRevision);
        }
        // 設定変更中のCaptureをworkerへ公開せず、AE2標準計算だけを残す。
        if (!PlanningConfigurationRevisionTracker.isCurrent(configurationRevision)) {
            return null;
        }
        // cold pathと証明不能な注文は在庫を読まず、非同期側の構造判定へ渡す。
        return new Capture(
                level,
                grid,
                source,
                networkSnapshot,
                null,
                graphCapture,
                ArithmeticCaptureMode.UNASSESSED,
                patternGeneration,
                recipeGeneration,
                storageRevision,
                configurationRevision);
    }

    private static boolean hasCachedLongSafetyCertificate(
            Ae2ImmutablePlanningGraphCache.RootCapture graphCapture,
            AEKey output,
            long requestedAmount,
            long patternGeneration,
            long recipeGeneration) {
        if (graphCapture.patternGeneration() != patternGeneration
                || graphCapture.recipeGeneration() != recipeGeneration) {
            return false;
        }
        Ae2PlanningGraphSnapshot graphSnapshot = graphCapture.compiledSnapshot().orElse(null);
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
        return tryPlanAttempt(capture, output, requestedAmount, strategy, null);
    }

    /** Issue #179: AE2 tick待機を解除した区間でだけ呼ぶ。Server Threadはworkerを待たない。 */
    public static ICraftingPlan tryPlanDetached(
            @Nullable Capture capture,
            AEKey output,
            long requestedAmount,
            CalculationStrategy strategy,
            PlanningWorkerYield resume) {
        // 同期APIを誤ってこの入口へ渡し、Server自身をget待ちにしない。
        if (capture != null && Thread.currentThread() == capture.serverThread()) {
            throw new IllegalStateException("detached planning requires the calculation worker");
        }
        Objects.requireNonNull(resume, "resume");
        return tryPlanAttempt(capture, output, requestedAmount, strategy, detachedWorkerYield(resume));
    }

    static PlanningWorkerYield detachedWorkerYield(PlanningWorkerYield resume) {
        return new PlanningWorkerYield() {
            private boolean resumed;

            @Override
            public void yieldToServerThread() throws InterruptedException {
                if (resumed) {
                    resume.yieldToServerThread();
                    return;
                }
                // 不変計算はtickを待たず、既存checkpointで取消だけを受け取る。
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }
            }

            @Override
            public boolean waitsForServerTick() {
                return resumed;
            }

            @Override
            public void beforeResult() {
                if (resumed) return;
                resumed = true;
                try {
                    resume.yieldToServerThread();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new PlanningCancelledException(0);
                }
            }
        };
    }

    /**
     * AE2の計算workerから呼ぶ入口。Graphと数量の既存checkpoint、および
     * Server Thread上のexact取得待機からCraftingCalculation pause handshakeへ制御を返す。
     */
    public static ICraftingPlan tryPlan(
            @Nullable Capture capture,
            AEKey output,
            long requestedAmount,
            CalculationStrategy strategy,
            PlanningWorkerYield workerYield) {
        return tryPlanAttempt(
                capture,
                output,
                requestedAmount,
                strategy,
                Objects.requireNonNull(workerYield, "workerYield"));
    }

    @Nullable
    private static ICraftingPlan tryPlanAttempt(
            @Nullable Capture capture,
            AEKey output,
            long requestedAmount,
            CalculationStrategy strategy,
            @Nullable PlanningWorkerYield workerYield) {
        return retryStalePlan(capture,
                current -> tryPlanOnce(current, output, requestedAmount, strategy, workerYield),
                current -> refreshCapture(current, output, requestedAmount, workerYield));
    }

    /** Issue #190: each retry owns a new inventory/graph pair, not a new label on old data. */
    static <C, T> T retryStalePlan(C capture, Function<C, T> plan, Function<C, C> refresh) {
        C current = capture;
        StalePlanningSnapshotException lastStale = null;
        for (int attempt = 0; ; attempt++) {
            if (Thread.currentThread().isInterrupted()) throw new PlanningCancelledException(0);
            T result;
            try {
                if (attempt > 0) current = refresh.apply(current);
                result = plan.apply(current);
            } catch (StalePlanningSnapshotException stale) {
                if (attempt >= 2) throw stale;
                lastStale = stale;
                continue;
            }
            // The caller's vanilla inventory still belongs to the initial capture.
            if (attempt > 0 && result == null) throw lastStale;
            return result;
        }
    }

    private static Capture refreshCapture(Capture previous, AEKey output, long requestedAmount,
            @Nullable PlanningWorkerYield workerYield) {
        Supplier<Capture> action = () -> {
            StorageRevisionTracker.RevisionToken storage = StorageRevisionTracker.refreshAndCapture(previous.grid());
            long pattern = ProviderPatternGenerationTracker.generation();
            long recipe = RecipeGenerationTracker.generation();
            long configuration = PlanningConfigurationRevisionTracker.current();
            Capture fresh = Ae2PlanningCaptureCoordinator.capture(previous.level(), previous.grid(), previous.source(),
                    previous.grid().getStorageService().getCachedInventory(), output, requestedAmount,
                    storage, pattern, recipe, configuration).authoritative();
            if (fresh == null) {
                throw new StalePlanningSnapshotException(
                        new PlanningGenerationSnapshot(pattern, storage.revision(), recipe), 0);
            }
            fresh.requireCurrentGenerations();
            return fresh;
        };
        if (Thread.currentThread() == previous.serverThread()) return action.get();
        if (previous.server() == null) throw new IllegalStateException("recapture requires a server level");
        if (workerYield != null && !workerYield.waitsForServerTick()) {
            return PlanningServerTasks.call(previous.server(), action);
        }
        java.util.concurrent.FutureTask<Capture> task = new java.util.concurrent.FutureTask<>(action::get);
        previous.server().execute(task);
        try {
            cooperativelyAwait(task, workerYield, true);
            return task.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new PlanningCancelledException(0);
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failed.getCause() instanceof Error fatal) throw fatal;
            throw new IllegalStateException("planning recapture failed", failed.getCause());
        } finally {
            task.cancel(false);
        }
    }

    @Nullable
    private static ICraftingPlan tryPlanOnce(
            @Nullable Capture capture,
            AEKey output,
            long requestedAmount,
            CalculationStrategy strategy,
            @Nullable PlanningWorkerYield workerYield) {
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

        boolean wideArithmeticRequired = false;
        try {
            Ae2ImmutablePlanningGraphCache.RootCapture immutableCapture =
                    capture.planningGraphCapture();
            // root captureが無い互換入口は、mutable serviceをworkerから読まずAE2へ戻す。
            if (immutableCapture == null) {
                return null;
            }
            PlanningGuard guard = planningGuard(workerYield);
            /*
             * Issue #179: CraftingCalculationはAE2自身のCRAFTING_POOL上で動作する。
             * 第二executorへ再投入すると全Gridを直列化するため、不変Snapshotの純粋計算は
             * 現在のAE2計算workerで直接実行し、既存checkpointからAE2の
             * handlePausing()へ制御を返す。
             */
            PlanningPreparation preparation = preparePlanning(
                    immutableCapture,
                    output,
                    requestedAmount,
                    longSafetyCertified,
                    guard);
            wideArithmeticRequired = preparation.wideArithmeticRequired();
            // 構造上コンパイル不能なルートは、理由を保持してAE2へ戻す。
            if (!preparation.ready()) {
                // Issue #190: a branching graph is not an ambiguous plan. Preserve AE2's trial order.
                if (ACOConfig.enableProofQualifiedLongPlans()) {
                    ICraftingPlan branching = tryBranchingPlan(capture, immutableCapture.compile(guard),
                            output, requestedAmount, strategy, workerYield, guard);
                    if (branching != null) return branching;
                }
                if (preparation.rootFailure() != RootProgramFailure.NONE) {
                    logRootProgramFailureOnce(output, preparation.rootFailure(), capture);
                }
                return declineOrThrow(
                        capture,
                        output,
                        requestedAmount,
                        wideArithmeticRequired,
                        preparation.declineReason(),
                        preparation.detail());
            }
            Ae2PlanningGraphSnapshot graphSnapshot = preparation.graphSnapshot();
            CompiledRootProgram<AEKey> program = preparation.program();
            Ae2StrictCraftingTopology topology = preparation.topology();
            /*
             * Issue #167: cold captureではwide判定前に世代変更が起こり得る。
             * capture済みimmutable graphだけでwide性を確定してからstaleを処理し、
             * overflowする注文をAE2標準long経路へ誤って戻さない。
             */
            capture.requireCurrentGenerations();
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
                        captureExactInventorySnapshot(capture, workerYield));
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
                                    output,
                                    guard);
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
                        output,
                        guard);
            }
            OverflowPromotingCraftingPlanner<AEKey> planner =
                    new OverflowPromotingCraftingPlanner<>(
                            ACOConfig.getBigIntegerMaximumBits());
            final CompiledRootProgram.BigInventorySnapshot<AEKey> exactInventorySnapshot = exactPlanningInventory;
            final CompiledRootProgram.InventorySnapshot<AEKey> longInventorySnapshot = planningInventory;
            OverflowPromotingCraftingPlanner.Result<AEKey> promoted =
                    exactInventorySnapshot != null
                            ? planner.plan(
                                    program,
                                    BigInteger.valueOf(requestedAmount),
                                    exactInventorySnapshot,
                                    guard)
                            : planner.plan(
                                    program,
                                    BigInteger.valueOf(requestedAmount),
                                    longInventorySnapshot,
                                    guard);
            capture.requireCurrentGenerations();
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
            NormalizedPlan symbolic = normalize(promoted, program, guard);
            ICraftingPlan result;
            BigCraftingPlan<AEKey> exactPlan = exactPlan(promoted);
            boolean widePlan = wideArithmeticRequired
                    || promoted.usesBigInteger()
                    || symbolic == null;
            if (program.usesOrderedAccounting() && !exactPlan.craftable()
                    && strategy == CalculationStrategy.CRAFT_LESS) {
                return declineOrThrow(capture, output, requestedAmount, widePlan,
                        BigIntegerPlanDeclineReason.UNSUPPORTED_TOPOLOGY,
                        "partial byproduct search requires AE2 retained-tree accounting");
            }
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
                    OverflowPromotingCraftingPlanner.Result<AEKey> partialPlan =
                            partialAmount.signum() <= 0
                                    ? null
                                    : exactPlanAt(
                                            planner,
                                            program,
                                            partialAmount,
                                            exactInventorySnapshot,
                                            longInventorySnapshot,
                                            guard);
                    if (partialAmount.signum() <= 0) {
                        result = createBigIntegerSimulationPlan(
                                capture, workerYield,
                                graphSnapshot,
                                topology,
                                output,
                                BigInteger.valueOf(requestedAmount),
                                exactPlan,
                                null);
                    } else {
                        result = createBigIntegerParentPlan(
                                capture,
                                workerYield,
                                graphSnapshot,
                                program,
                                topology,
                                output,
                                partialAmount,
                                strategy,
                                partialPlan,
                                true,
                                null);
                    }
                } else {
                    result = createBigIntegerSimulationPlan(
                            capture, workerYield,
                            graphSnapshot,
                            topology,
                            output,
                            BigInteger.valueOf(requestedAmount),
                            exactPlan,
                            null);
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
                            workerYield,
                            graphSnapshot,
                            program,
                            topology,
                            output,
                            BigInteger.valueOf(requestedAmount),
                            strategy,
                            promoted,
                            true,
                            null);
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
                            workerYield,
                            graphSnapshot,
                            topology,
                            output,
                            requestedAmount,
                            strategy,
                            symbolic,
                            wideArithmeticRequired,
                            null);
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

            // Issue #179: 次tickへの復帰待ち中にも世代は変わる。復帰した後で最後に検証する。
            if (workerYield != null) {
                workerYield.beforeResult();
            }
            capture.requireCurrentGenerations();
            // Emitterまたはrecipe世代が変わった場合も、AE2と選択結果がずれるため破棄する。
            if (!topology.remainsCurrent()) {
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
                    Thread.currentThread().isInterrupted(),
                    wideArithmeticRequired);
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
            recordDecline(
                    capture,
                    output,
                    requestedAmount,
                    BigIntegerPlanDeclineReason.GENERATION_CHANGED,
                    stale.getMessage() + "; recovery=" + action);
            // Issue #190: retry with a fresh capture, never run the expensive standard path on a stale index.
            if (action == StaleSnapshotAction.REFRESH_CAPTURE) {
                throw stale;
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
            // Issue #167: ACO内部異常を「対応外」と誤診断してAE2へ流さず、元例外を保持する。
            throw new IllegalStateException(
                    "ACO authoritative planner failed for " + output.getId(),
                    failure);
        }
    }

    private static PlanningPreparation preparePlanning(
            Ae2ImmutablePlanningGraphCache.RootCapture immutableCapture,
            AEKey output,
            long requestedAmount,
            boolean longSafetyCertified,
            PlanningGuard guard) {
        Ae2PlanningGraphSnapshot graphSnapshot = immutableCapture.compile(guard);
        CompiledRootProgram.Outcome<AEKey> rootOutcome =
                graphSnapshot.rootProgramOutcome(output, guard);
        var optionalProgram = rootOutcome.program();
        // Rootが決定的な配列Programにならない場合は、理由だけを呼出側へ返す。
        if (optionalProgram.isEmpty()) {
            return PlanningPreparation.declined(
                    rootOutcome.failure(),
                    classifyRootProgramFailure(rootOutcome.failure()),
                    "root program unavailable: " + rootOutcome.failure());
        }
        CompiledRootProgram<AEKey> program = optionalProgram.get();
        Ae2StrictCraftingTopology topology = graphSnapshot
                .strictTopology(program, guard)
                .orElse(null);
        // AE2と同じ候補・入力を証明できない構造は、高速経路で計算しない。
        if (topology == null || !topology.acceptsInventory()) {
            return PlanningPreparation.declined(
                    RootProgramFailure.NONE,
                    BigIntegerPlanDeclineReason.UNSUPPORTED_TOPOLOGY,
                    "strict topology was not proven");
        }
        boolean wideArithmeticRequired = !longSafetyCertified
                && topology.mightRequireWideArithmetic(
                        output,
                        BigInteger.valueOf(requestedAmount),
                        ACOConfig.getBigIntegerMaximumBits(),
                        guard);
        return PlanningPreparation.ready(
                graphSnapshot,
                program,
                topology,
                wideArithmeticRequired);
    }

    @Nullable
    private static ICraftingPlan tryBranchingPlan(Capture capture, Ae2PlanningGraphSnapshot snapshot,
            AEKey output, long requestedAmount, CalculationStrategy strategy,
            @Nullable PlanningWorkerYield workerYield, PlanningGuard guard) {
        capture.requireCurrentGenerations();
        Ae2BranchingInputRules rules = branchingRules(capture, snapshot, capture.inventorySnapshot(), workerYield, guard);
        boolean[] saturatedStock = {false};
        Function<AEKey, BigInteger> stock = key -> {
            long amount = capture.inventorySnapshot().amount(key);
            saturatedStock[0] |= amount == Long.MAX_VALUE;
            return BigInteger.valueOf(amount);
        };
        OrderedBranchingPlanner.Result<AEKey> result;
        try {
            result = evaluateBranching(snapshot, output, requestedAmount, strategy, stock, guard,
                    ACOConfig.getBigIntegerMaximumBits(), rules);
        } catch (UncapturedBranch unsupported) {
            recordDecline(capture, output, requestedAmount, BigIntegerPlanDeclineReason.INCOMPLETE_GRAPH_SNAPSHOT,
                    "branch input unavailable: " + unsupported.getMessage());
            return null;
        }
        boolean wide = branchingRequiresWide(result.plan(), snapshot);
        if (wide || saturatedStock[0]) {
            KeyCounter exactInventory = capture.exactInventorySnapshot() != null
                    ? capture.exactInventorySnapshot() : captureExactInventorySnapshot(capture, workerYield);
            var exact = BigKeyCounterSidecars.snapshot(exactInventory).orElse(null);
            if (exact == null) throw new WidePlanUnavailableException(output, "branching exact inventory unavailable");
            rules = branchingRules(capture, snapshot, Ae2PlanningInventorySnapshot.capture(exactInventory), workerYield, guard);
            try {
                result = evaluateBranching(snapshot, output, requestedAmount, strategy, key -> {
                    if (!exact.isExact(key)) throw new UncapturedBranch("unproven exact inventory: " + key.getId());
                    return exact.amount(key);
                }, guard, ACOConfig.getBigIntegerMaximumBits(), rules);
            } catch (UncapturedBranch unavailable) {
                throw new WidePlanUnavailableException(output, unavailable.getMessage());
            }
            wide = branchingRequiresWide(result.plan(), snapshot);
        }
        if (wide && !ACOConfig.enableBigIntegerGameplayExecution()) {
            throw new WidePlanUnavailableException(output, "exact branching simulation is disabled");
        }
        BigCraftingPlan<AEKey> plan = result.plan();
        BigInteger bytes = BigExactCraftingByteCounter.calculate(plan.trace(),
                key -> key.getType().getAmountPerByte(), ACOConfig.getBigIntegerMaximumBits());
        OrderedBranchingPlanner.Result<AEKey> completed = result;
        boolean wideSimulation = wide;
        com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatch selectedBranch = null;
        if (wide && plan.craftable()) {
            // Issue #190: preserve the selected DAG; never re-plan it at CPU submission.
            if (!ACOConfig.enableExactBigIntegerPhysicalExecution()) {
                throw new WidePlanUnavailableException(output, "selected branch physical execution is disabled");
            }
            if (plan.patternExecutions().size() > ACOConfig.getExactVectorMaximumPatternNodes()) {
                throw new WidePlanUnavailableException(output, "selected branch exceeds the physical pattern bound");
            }
            for (String id : plan.patternExecutions().keySet()) {
                if (!snapshot.hasExactInputDomain(id)) {
                    throw new WidePlanUnavailableException(output, "selected branch has unbound dynamic inputs: " + id);
                }
            }
            try {
                selectedBranch = SelectedBranchPhysicalPlan.prepare(plan, snapshot.graph().patterns(),
                        capture.patternGeneration(), capture.recipeGeneration(), ACOConfig.getBigIntegerMaximumBits());
                com.syaru.ae2craftingoptimizer.engine.vector.VectorBatchPlanValidator.validate(selectedBranch,
                        ACOConfig.getBigIntegerMaximumBits(), ACOConfig.getExactVectorMaximumPatternNodes(),
                        ACOConfig.getExactVectorMaximumInputKeys(), ACOConfig.getExactVectorMaximumOutputKeys());
            } catch (IllegalArgumentException unsupported) {
                throw new WidePlanUnavailableException(output, unsupported.getMessage());
            }
        }
        var physicalBranch = selectedBranch;
        // Validate bounded batches while detached; never put the whole observation set in one server task.
        rules.revalidate();
        ICraftingPlan materialized = materialize(capture, workerYield, () -> {
            capture.requireCurrentGenerations();
            Map<IPatternDetails, BigInteger> exactTimes = resolveExactPatternTimes(snapshot, plan.patternExecutions());
            if (exactTimes == null) throw new IllegalStateException("branching pattern binding unavailable");
            if (wideSimulation) {
                if (physicalBranch != null) {
                    try {
                        SelectedBranchPhysicalPlan.validateBindings(physicalBranch, snapshot::pattern,
                                capture.level(), ACOConfig.getBigIntegerMaximumBits());
                    } catch (IllegalArgumentException unsupported) {
                        throw new WidePlanUnavailableException(output, unsupported.getMessage());
                    }
                    var prepared = new Ae2BigCraftingPlanFactory.PreparedBigRootPlan(null, plan, bytes,
                            capture.patternGeneration(), capture.recipeGeneration(),
                            Ae2BigCraftingPlanFactory.ExecutionMode.EXACT_PATTERN_EXECUTOR, 0L,
                            PlanningRuntimeEpoch.current(), physicalBranch.programFingerprint());
                    return Ae2CraftingPlanSidecars.expose(new BigIntegerCraftingPlan(
                            new GenericStack(output, plan.requestedAmount().longValueExact()), plan, exactTimes,
                            prepared, true, physicalBranch, completed.multiplePaths()));
                }
                return Ae2CraftingPlanSidecars.expose(new BigIntegerSimulationPlan(
                        new GenericStack(output, plan.requestedAmount().longValueExact()), plan, exactTimes,
                        bytes, ACOConfig.getBigIntegerMaximumBits(), completed.multiplePaths()));
            }
            Map<IPatternDetails, Long> times = new LinkedHashMap<>();
            exactTimes.forEach((pattern, count) -> times.put(pattern, count.longValueExact()));
            GenericStack finalOutput = new GenericStack(output, plan.requestedAmount().longValueExact());
            KeyCounter used = keyCounter(exactLongCounter(plan.usedInventory()));
            KeyCounter emitted = keyCounter(exactLongCounter(plan.emitted()));
            KeyCounter missing = keyCounter(exactLongCounter(plan.missing()));
            ICraftingPlan facade;
            if (bytes.bitLength() > 63) {
                if (!ACOConfig.enableAtomicBigCapacityPlans()) {
                    throw new WidePlanUnavailableException(output, "exact capacity plans are disabled");
                }
                facade = Ae2CraftingPlanSidecars.expose(new BigCapacityCraftingPlan(finalOutput,
                        !plan.craftable(), completed.multiplePaths(), used, emitted, missing, Map.copyOf(times),
                        bytes, capture.patternGeneration(), capture.recipeGeneration()));
            } else {
                long normalBytes = completed.ae2Bytes();
                facade = new CraftingPlan(finalOutput, normalBytes, !plan.craftable(), completed.multiplePaths(),
                        used, emitted, missing, Map.copyOf(times));
            }
            return facade;
        });
        // Issue #179/#190: bind while detached, before AE2 resumes waiting for this worker.
        if (workerYield != null) workerYield.beforeResult();
        capture.requireCurrentGenerations();
        if (ACOConfig.logCraftingDecisionFlow()) {
            AE2CraftingOptimizer.LOGGER.debug(
                    "ACO-DIAG event=planning_accepted route=ordered-branching output={} requested={} "
                            + "patterns={} expanded={} skippedIterations={} simulation={}",
                    output.getId(), requestedAmount, plan.patternExecutions().size(), plan.expandedRequests(),
                    completed.skippedIterations(), materialized.simulation());
        }
        return materialized;
    }

    static OrderedBranchingPlanner.Result<AEKey> evaluateBranching(Ae2PlanningGraphSnapshot snapshot,
            AEKey output, long requestedAmount, CalculationStrategy strategy,
            Function<AEKey, BigInteger> stock, PlanningGuard guard, int maximumBits) {
        return evaluateBranching(snapshot, output, requestedAmount, strategy, stock, guard, maximumBits, null);
    }

    static OrderedBranchingPlanner.Result<AEKey> evaluateBranching(Ae2PlanningGraphSnapshot snapshot,
            AEKey output, long requestedAmount, CalculationStrategy strategy,
            Function<AEKey, BigInteger> stock, PlanningGuard guard, int maximumBits,
            BranchingInputRules<AEKey> rules) {
        return new OrderedBranchingPlanner<>(output, key -> {
            var candidates = snapshot.orderedPatternsFor(key);
            if (snapshot.isIncompletelyCompiled(key) || snapshot.registeredPatternCount(key) != candidates.size()) {
                throw new UncapturedBranch(key.getId() + " (registered=" + snapshot.registeredPatternCount(key)
                        + ", captured=" + candidates.size() + ")");
            }
            for (var pattern : candidates) {
                if (rules == null && !snapshot.hasExactInputDomain(pattern.id())) {
                    throw new UncapturedBranch("dynamic input: " + pattern.id());
                }
            }
            return candidates;
        }, snapshot::isEmittable, stock, key -> key.getType().getAmountPerByte(), guard,
                maximumBits, rules).plan(BigInteger.valueOf(requestedAmount),
                        strategy == CalculationStrategy.CRAFT_LESS);
    }

    private static Ae2BranchingInputRules branchingRules(Capture capture, Ae2PlanningGraphSnapshot snapshot,
            Ae2PlanningInventorySnapshot inventory, PlanningWorkerYield workerYield, PlanningGuard guard) {
        return new Ae2BranchingInputRules(snapshot, inventory, () -> capture.grid().getCraftingService(),
                capture.level(), new Ae2BranchingInputRules.ServerCall() {
                    @Override public <T> T call(Supplier<T> action) {
                        return inputObservationOnServer(capture, workerYield, action);
                    }
                }, guard);
    }

    private static <T> T inputObservationOnServer(Capture capture, PlanningWorkerYield workerYield,
            Supplier<T> action) {
        Supplier<T> checked = () -> {
            capture.requireCurrentGenerations();
            T value = action.get();
            capture.requireCurrentGenerations();
            return value;
        };
        if (Thread.currentThread() == capture.serverThread()) return checked.get();
        if (capture.server() == null) throw new IllegalStateException("input capture requires a server level");
        if (workerYield != null && !workerYield.waitsForServerTick()) {
            return PlanningServerTasks.call(capture.server(), checked);
        }
        var task = new java.util.concurrent.FutureTask<T>(checked::get);
        capture.server().execute(task);
        try {
            cooperativelyAwait(task, workerYield, true);
            return task.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new PlanningCancelledException(0);
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failed.getCause() instanceof Error fatal) throw fatal;
            throw new IllegalStateException("input capture failed", failed.getCause());
        } finally {
            task.cancel(false);
        }
    }

    private static boolean branchingRequiresWide(BigCraftingPlan<AEKey> plan, Ae2PlanningGraphSnapshot snapshot) {
        for (Map<?, BigInteger> values : List.of(plan.patternExecutions(), plan.usedInventory(),
                plan.emitted(), plan.missing())) {
            if (values.values().stream().anyMatch(value -> value.bitLength() > 63)) return true;
        }
        Map<AEKey, BigInteger> totals = new LinkedHashMap<>();
        Map<String, CompiledPattern<AEKey>> patterns = new LinkedHashMap<>();
        snapshot.graph().patterns().forEach(pattern -> patterns.put(pattern.id(), pattern));
        for (var entry : plan.patternExecutions().entrySet()) {
            CompiledPattern<AEKey> pattern = patterns.get(entry.getKey());
            if (pattern == null) throw new IllegalStateException("branching pattern disappeared");
            for (var slot : pattern.inputs()) {
                BigInteger amount = entry.getValue().multiply(BigInteger.valueOf(slot.alternatives().get(0).amount()));
                if (amount.bitLength() > 63) return true;
            }
            for (var produced : pattern.outputs().entrySet()) {
                BigInteger total = totals.merge(produced.getKey(),
                        entry.getValue().multiply(BigInteger.valueOf(produced.getValue())), BigInteger::add);
                if (total.bitLength() > 63) return true;
            }
        }
        return false;
    }

    private static final class UncapturedBranch extends RuntimeException {
        private UncapturedBranch(String detail) { super(detail); }
    }

    private static PlanningGuard planningGuard(
            @Nullable PlanningWorkerYield workerYield) {
        return expanded -> {
            // 不変Snapshotの世代は結果適用前に検証し、worker中はキャンセルとAE2のpauseだけを扱う。
            if (Thread.currentThread().isInterrupted()) {
                throw new PlanningCancelledException(expanded);
            }
            // API直接呼出しではAE2 CraftingCalculationのpause実装が存在しない。
            if (workerYield == null) {
                return;
            }
            try {
                workerYield.yieldToServerThread();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new PlanningCancelledException(expanded);
            }
        };
    }

    @Nullable
    private static ICraftingPlan createBigIntegerParentPlan(
            Capture capture,
            @Nullable PlanningWorkerYield workerYield,
            Ae2PlanningGraphSnapshot graphSnapshot,
            CompiledRootProgram<AEKey> program,
            Ae2StrictCraftingTopology topology,
            AEKey output,
            BigInteger requestedAmount,
            CalculationStrategy strategy,
            OverflowPromotingCraftingPlanner.Result<AEKey> promoted,
            boolean requiresBigIntegerExecution,
            @Nullable BigInteger plannedExactBytes) {
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

        BigInteger requested = requestedAmount;
        BigInteger exactBytes = plannedExactBytes != null
                ? plannedExactBytes
                : topology.calculateBigExactBytes(
                        output,
                        requested,
                        exactPlan.patternExecutions(),
                        ACOConfig.getBigIntegerMaximumBits(), exactPlan.trace());
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
        return materialize(capture, workerYield, () -> {
            Map<IPatternDetails, BigInteger> exactPatternTimes = resolveExactPatternTimes(
                    graphSnapshot, exactPlan.patternExecutions());
            // Pattern参照を同一世代へ戻せない計画は採用しない。
            if (exactPatternTimes == null) {
                return null;
            }
            BigIntegerCraftingPlan metadata = new BigIntegerCraftingPlan(
                    new GenericStack(output, requested.longValueExact()),
                    exactPlan, exactPatternTimes, prepared, requiresBigIntegerExecution);
            return Ae2CraftingPlanSidecars.expose(metadata);
        });
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
            Capture capture,
            @Nullable PlanningWorkerYield workerYield,
            Ae2PlanningGraphSnapshot graphSnapshot,
            Ae2StrictCraftingTopology topology,
            AEKey output,
            BigInteger requestedAmount,
            BigCraftingPlan<AEKey> exactPlan,
            @Nullable BigInteger plannedExactBytes) {
        BigInteger exactBytes = plannedExactBytes != null
                ? plannedExactBytes
                : topology.calculateBigExactBytes(
                        output,
                        requestedAmount,
                        exactPlan.patternExecutions(),
                        ACOConfig.getBigIntegerMaximumBits(), exactPlan.trace());
        return materialize(capture, workerYield, () -> {
            Map<IPatternDetails, BigInteger> exactPatternTimes = resolveExactPatternTimes(
                    graphSnapshot, exactPlan.patternExecutions());
            // 不足simulationも実行計画と同じPattern参照条件を満たす。
            if (exactPatternTimes == null) {
                return null;
            }
            return Ae2CraftingPlanSidecars.expose(new BigIntegerSimulationPlan(
                    new GenericStack(output, requestedAmount.longValueExact()),
                    exactPlan, exactPatternTimes, exactBytes));
        });
    }

    @Nullable
    private static Map<IPatternDetails, BigInteger> resolveExactPatternTimes(
            Ae2PlanningGraphSnapshot graphSnapshot,
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

    /** Root Programの失敗理由を公開統計の安定したコードへ変換する。 */
    static BigIntegerPlanDeclineReason classifyRootProgramFailure(RootProgramFailure failure) {
        return switch (failure) {
            case CYCLE -> BigIntegerPlanDeclineReason.CYCLE;
            case MULTIPLE_PRODUCERS -> BigIntegerPlanDeclineReason.AMBIGUOUS_PRODUCER;
            case MULTIPLE_OUTPUTS, COUPLED_OUTPUTS -> BigIntegerPlanDeclineReason.UNSUPPORTED_PATTERN;
            case PROGRAM_TOO_LARGE -> BigIntegerPlanDeclineReason.PROGRAM_TOO_LARGE;
            case INCOMPLETE_PATTERN_SNAPSHOT, MISSING_FROM_SNAPSHOT ->
                    BigIntegerPlanDeclineReason.INCOMPLETE_GRAPH_SNAPSHOT;
            case NONE -> BigIntegerPlanDeclineReason.NO_COMPILED_PROGRAM;
        };
    }

    /** 同一revisionの不変Captureが不完全だった場合だけ、世代ごとに一度警告する。 */
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
                + ":" + capture.recipeGeneration()
                + ":" + capture.configurationRevision();
        // 世代が進み続けても常駐量が増え続けないよう、固定上限で表を再利用する。
        if (LOGGED_SNAPSHOT_FALLBACKS.size() >= MAXIMUM_LOGGED_SNAPSHOT_FALLBACKS) {
            LOGGED_SNAPSHOT_FALLBACKS.clear();
        }
        if (!LOGGED_SNAPSHOT_FALLBACKS.add(key)) {
            return;
        }
        AE2CraftingOptimizer.LOGGER.warn(
                "ACO could not compile {} because the immutable crafting graph capture was incomplete"
                        + " (reason={}, patternGeneration={}, recipeGeneration={}, configurationRevision={}).",
                output.getId(),
                failure,
                capture.patternGeneration(),
                capture.recipeGeneration(),
                capture.configurationRevision());
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
                0, source.trace());
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
            @Nullable PlanningWorkerYield workerYield,
            Ae2PlanningGraphSnapshot graphSnapshot,
            Ae2StrictCraftingTopology topology,
            AEKey output,
            long requestedAmount,
            CalculationStrategy strategy,
            NormalizedPlan symbolic,
            boolean fullExpansionRequiresWideArithmetic,
            @Nullable BigInteger plannedExactBytes) {
        // CRAFT_LESSの部分成功探索はAE2へ任せ、近似した出力量を返さない。
        if (!symbolic.craftable() && strategy == CalculationStrategy.CRAFT_LESS) {
            return null;
        }

        boolean wideInputAggregate = symbolic.hasAggregatePastLong();
        BigInteger exactBytes = plannedExactBytes != null
                ? plannedExactBytes
                : topology.calculateBigExactBytes(
                        output,
                        BigInteger.valueOf(requestedAmount),
                        symbolic.bigPatternExecutions(),
                        ACOConfig.getBigIntegerMaximumBits(), symbolic.trace());
        // bytesのtree走査はbinding確定より前にworker上で完了させる。
        boolean bigCapacity = exactBytes.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0;
        long facadeBytes = bigCapacity ? 0L
                : fullExpansionRequiresWideArithmetic || wideInputAggregate
                        ? exactBytes.longValueExact()
                        : topology.calculateAe2LongBytes(output, requestedAmount, symbolic.patternExecutions(),
                                symbolic.trace());
        return materialize(capture, workerYield, () -> {
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
            // 容量合計だけがlongを超える場合、個別カウンタはlongのままAQE Sidecarへ真値を渡す。
            if (bigCapacity) {
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
                    facadeBytes,
                    !symbolic.craftable(),
                    false,
                    keyCounter(symbolic.usedInventory()),
                    keyCounter(symbolic.emitted()),
                    keyCounter(symbolic.missing()),
                    Map.copyOf(patternTimes));
        });
    }

    private static ICraftingPlan materialize(
            Capture capture,
            @Nullable PlanningWorkerYield workerYield,
            Supplier<ICraftingPlan> factory) {
        // API直接呼出しと登録済み互換入口の所有thread契約は変えない。
        if (workerYield == null || workerYield.waitsForServerTick()) {
            return factory.get();
        }
        return PlanningServerTasks.call(capture.server(), () -> {
            // 結果のbinding直前にも世代を検査し、旧snapshotの世代付け替えはしない。
            capture.requireCurrentGenerations();
            return factory.get();
        });
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
        REFRESH_CAPTURE,
        REJECT_WIDE,
        CANCEL
    }

    /**
     * 世代競合時の回復方法を、通常long計画とwide計画で分離する。
     */
    static StaleSnapshotAction staleSnapshotAction(
            boolean interrupted,
            boolean wideArithmeticRequired) {
        // AE2のキャンセル要求は再試行や標準計算より優先する。
        if (interrupted) {
            return StaleSnapshotAction.CANCEL;
        }
        // wideと判明済みの計画をoverflowするAE2標準long計算へ落とさない。
        if (wideArithmeticRequired) {
            return StaleSnapshotAction.REJECT_WIDE;
        }
        return StaleSnapshotAction.REFRESH_CAPTURE;
    }

    private static boolean normalLongReplacementEnabled() {
        return normalLongReplacementEnabled(
                ACOConfig.enableCompiledCraftingGraph(),
                ACOConfig.enableAuthoritativeCompiledPlanner(),
                ACOConfig.enableProofQualifiedLongPlans());
    }

    static boolean normalLongReplacementEnabled(
            boolean compiledGraphEnabled,
            boolean authoritativePlannerEnabled,
            boolean proofQualifiedLongPlansEnabled) {
        // compiled graphがOFFなら、下位の置換設定が残っていても通常long計画へ介入しない。
        if (!compiledGraphEnabled) {
            return false;
        }
        // 厳密証明を通過した二つの採用経路だけが、通常long計画を置換できる。
        return authoritativePlannerEnabled || proofQualifiedLongPlansEnabled;
    }

    public static boolean planningEnabled() {
        return normalLongReplacementEnabled()
                || ACOConfig.enableAtomicBigCapacityPlans();
    }

    @Nullable
    private static NormalizedPlan normalize(
            OverflowPromotingCraftingPlanner.Result<AEKey> promoted,
            CompiledRootProgram<AEKey> program,
            PlanningGuard guard) {
        try {
            // long高速経路は容量式用のPattern回数だけBigIntegerへ無損失変換する。
            if (promoted instanceof OverflowPromotingCraftingPlanner.LongResult<AEKey> result) {
                LongCraftingPlan<AEKey> plan = result.plan();
                return new NormalizedPlan(
                        plan.patternExecutions(),
                        bigPatternCounter(plan.patternExecutions()),
                        plan.usedInventory(),
                        plan.emitted(),
                        plan.missing(), plan.trace());
            }
            // overflow昇格後も、AE2へ渡す全個別値がlongへ正確に戻せる場合だけ採用する。
            if (promoted instanceof OverflowPromotingCraftingPlanner.BigResult<AEKey> result) {
                BigCraftingPlan<AEKey> plan = result.plan();
                if (program.requiresWideOutputCounts(
                        plan.patternExecutions(), ACOConfig.getBigIntegerMaximumBits(), guard)) {
                    return null;
                }
                return new NormalizedPlan(
                        exactLongCounter(plan.patternExecutions()),
                        plan.patternExecutions(),
                        exactLongCounter(plan.usedInventory()),
                        exactLongCounter(plan.emitted()),
                        exactLongCounter(plan.missing()), plan.trace());
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

    private static KeyCounter captureExactInventorySnapshot(
            Capture capture,
            @Nullable PlanningWorkerYield workerYield) {
        MinecraftServer server = capture.server();
        // サーバーを持たないLevelではexact正本を安全なスレッドへ委譲できないため失敗させる。
        if (server == null) {
            throw new IllegalStateException("exact inventory capture requires a server level");
        }
        // capture時に固定した所有thread上ならFutureを作らず、その場で一度だけ取得する。
        if (Thread.currentThread() == capture.serverThread()) {
            return captureExactInventoryOnServer(capture);
        }
        // Issue #179: tick待機を解除したworkerだけがServerの結果を直接待てる。
        if (workerYield != null && !workerYield.waitsForServerTick()) {
            return PlanningServerTasks.call(server, () -> captureExactInventoryOnServer(capture));
        }
        long waitStartedAt = System.nanoTime();
        if (ACOConfig.logCraftingDecisionFlow()) {
            AE2CraftingOptimizer.LOGGER.debug(
                    "ACO-DIAG event=exact_inventory_capture phase=scheduled storageGeneration={} thread={}",
                    capture.storageGeneration(),
                    Thread.currentThread().getName());
        }
        CompletableFuture<KeyCounter> pending = CompletableFuture.supplyAsync(
                () -> captureExactInventoryOnServer(capture),
                server);
        cooperativelyAwait(pending, workerYield, true);
        if (ACOConfig.logCraftingDecisionFlow()) {
            AE2CraftingOptimizer.LOGGER.debug(
                    "ACO-DIAG event=exact_inventory_capture phase=ready storageGeneration={} elapsedMicros={} thread={}",
                    capture.storageGeneration(),
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMicros(
                            System.nanoTime() - waitStartedAt),
                    Thread.currentThread().getName());
        }
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

    static void cooperativelyAwait(
            Future<?> pending,
            @Nullable PlanningWorkerYield workerYield,
            boolean workerYieldRequired) {
        Objects.requireNonNull(pending, "pending");
        // AE2 worker外のpure呼出しは、Server Thread処理を待たない場合だけ通常のgetへ進める。
        if (workerYield == null) {
            if (!workerYieldRequired || pending.isDone()) {
                return;
            }
            pending.cancel(false);
            throw new IllegalStateException(
                    "off-thread exact inventory capture requires AE2 cooperative yielding");
        }
        // 完了済みならpause handshakeは不要で、そのまま結果の取得へ進める。
        while (!pending.isDone()) {
            try {
                /*
                 * AE2 Server ThreadはsimulateFor()内でこのworkerのpauseを待つため、
                 * 別poolまたはServer executorのFutureを同期getする前に、同じ
                 * handlePausing() handshakeでtickへ制御を返す。
                 */
                workerYield.yieldToServerThread();
            } catch (InterruptedException interrupted) {
                pending.cancel(false);
                Thread.currentThread().interrupt();
                throw new PlanningCancelledException(0);
            } catch (RuntimeException | Error failure) {
                pending.cancel(false);
                throw failure;
            }
        }
    }

    /** AE2の遅延在庫差分を確定し、同じrevision内だけでexact mountを列挙する。 */
    private static KeyCounter captureExactInventoryOnServer(Capture capture) {
        StorageRevisionTracker.refreshAndCapture(capture.grid());
        // 外部mountの変更がAE2 cache更新で見つかった場合、旧long snapshotと混在させない。
        capture.requireCurrentGenerations();
        KeyCounter exact = PlanningExactInventorySnapshot.capture(capture.grid());
        // capture中の再入で在庫が変わった値も、旧revisionの計画へ公開しない。
        capture.requireCurrentGenerations();
        return exact;
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

    @FunctionalInterface
    public interface PlanningWorkerYield {
        void yieldToServerThread() throws InterruptedException;

        /** 既存callbackはAE2のpause handshakeを維持する。 */
        default boolean waitsForServerTick() {
            return true;
        }

        /** 切離し区間だけが、最終世代検証の前にAE2との排他を取り直す。 */
        default void beforeResult() {
        }
    }

    public record Capture(
            Level level,
            IGrid grid,
            IActionSource source,
            Ae2PlanningInventorySnapshot inventorySnapshot,
            @Nullable KeyCounter exactInventorySnapshot,
            @Nullable Ae2ImmutablePlanningGraphCache.RootCapture planningGraphCapture,
            ArithmeticCaptureMode arithmeticCaptureMode,
            long patternGeneration,
            long recipeGeneration,
            StorageRevisionTracker.RevisionToken storageRevision,
            long configurationRevision,
            @Nullable MinecraftServer server,
            @Nullable Thread serverThread) {
        /** 既存のCapture生成境界を保ち、server executor参照は呼出threadで固定する。 */
        public Capture(
                Level level,
                IGrid grid,
                IActionSource source,
                Ae2PlanningInventorySnapshot inventorySnapshot,
                @Nullable KeyCounter exactInventorySnapshot,
                @Nullable Ae2ImmutablePlanningGraphCache.RootCapture planningGraphCapture,
                ArithmeticCaptureMode arithmeticCaptureMode,
                long patternGeneration,
                long recipeGeneration,
                StorageRevisionTracker.RevisionToken storageRevision,
                long configurationRevision) {
            this(
                    level,
                    grid,
                    source,
                    inventorySnapshot,
                    exactInventorySnapshot,
                    planningGraphCapture,
                    arithmeticCaptureMode,
                    patternGeneration,
                    recipeGeneration,
                    storageRevision,
                    configurationRevision,
                    serverFor(level),
                    serverThreadFor(level));
        }

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
                    Ae2PlanningInventorySnapshot.capture(inventorySnapshot),
                    exactInventorySnapshot,
                    null,
                    ArithmeticCaptureMode.EXACT_AVAILABLE,
                    patternGeneration,
                    recipeGeneration,
                    captureStorageRevisionOnServer(level, grid),
                    PlanningConfigurationRevisionTracker.current());
        }

        public Capture {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(grid, "grid");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(inventorySnapshot, "inventorySnapshot");
            Objects.requireNonNull(arithmeticCaptureMode, "arithmeticCaptureMode");
            Objects.requireNonNull(storageRevision, "storageRevision");
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
            // Config 0以下は、実際に取得した設定状態を表さないため拒否する。
            if (configurationRevision <= 0L) {
                throw new IllegalArgumentException(
                        "configuration revision must be positive");
            }
            // server未取得なのに所有threadだけがあるCaptureは構築ミスなので拒否する。
            if (server == null && serverThread != null) {
                throw new IllegalArgumentException("server thread requires a server executor");
            }
        }

        public long storageGeneration() {
            return storageRevision.revision();
        }

        private void requireCurrentGenerations() {
            long currentPattern = ProviderPatternGenerationTracker.generation();
            long currentRecipe = RecipeGenerationTracker.generation();
            // Provider、recipe、参照在庫のいずれかが変わった計算結果は古いため破棄する。
            if (currentPattern != patternGeneration
                    || currentRecipe != recipeGeneration
                    || !StorageRevisionTracker.isCurrent(storageRevision)
                    || !PlanningConfigurationRevisionTracker.isCurrent(
                            configurationRevision)) {
                throw new StalePlanningSnapshotException(
                        new PlanningGenerationSnapshot(
                                patternGeneration,
                                storageRevision.revision(),
                                recipeGeneration),
                        0);
            }
        }

        private Capture withExactInventorySnapshot(KeyCounter exactSnapshot) {
            return new Capture(
                    level,
                    grid,
                    source,
                    inventorySnapshot,
                    Objects.requireNonNull(exactSnapshot, "exactSnapshot"),
                    planningGraphCapture,
                    ArithmeticCaptureMode.EXACT_AVAILABLE,
                    patternGeneration,
                    recipeGeneration,
                    storageRevision,
                    configurationRevision,
                    server,
                    serverThread);
        }

    }

    @Nullable
    private static MinecraftServer serverFor(@Nullable Level level) {
        return level == null ? null : level.getServer();
    }

    private static StorageRevisionTracker.RevisionToken captureStorageRevisionOnServer(
            Level level,
            IGrid grid) {
        // 旧公開constructorもoff-threadのlive Grid読取へ戻さない。
        if (!ServerPlanningThreadGuard.canCapture(level)) {
            throw new IllegalStateException(
                    "planning capture must be created on the server thread");
        }
        return StorageRevisionTracker.capture(grid);
    }

    @Nullable
    private static Thread serverThreadFor(@Nullable Level level) {
        MinecraftServer server = serverFor(level);
        // server thread以外で作られた互換Captureは、後で必ずexecutorへ委譲する。
        if (server == null || !server.isSameThread()) {
            return null;
        }
        return Thread.currentThread();
    }

    private record PlanningPreparation(
            @Nullable Ae2PlanningGraphSnapshot graphSnapshot,
            @Nullable CompiledRootProgram<AEKey> program,
            @Nullable Ae2StrictCraftingTopology topology,
            boolean wideArithmeticRequired,
            RootProgramFailure rootFailure,
            @Nullable BigIntegerPlanDeclineReason declineReason,
            String detail) {
        private static PlanningPreparation ready(
                Ae2PlanningGraphSnapshot graphSnapshot,
                CompiledRootProgram<AEKey> program,
                Ae2StrictCraftingTopology topology,
                boolean wideArithmeticRequired) {
            return new PlanningPreparation(
                    graphSnapshot,
                    program,
                    topology,
                    wideArithmeticRequired,
                    RootProgramFailure.NONE,
                    null,
                    "");
        }

        private static PlanningPreparation declined(
                RootProgramFailure rootFailure,
                BigIntegerPlanDeclineReason declineReason,
                String detail) {
            return new PlanningPreparation(
                    null,
                    null,
                    null,
                    false,
                    rootFailure,
                    declineReason,
                    detail);
        }

        private boolean ready() {
            return graphSnapshot != null && program != null && topology != null;
        }
    }

    private record NormalizedPlan(
            Map<String, Long> patternExecutions,
            Map<String, BigInteger> bigPatternExecutions,
            Map<AEKey, Long> usedInventory,
            Map<AEKey, Long> emitted,
            Map<AEKey, Long> missing,
            CraftingPlanTrace<AEKey> trace) {
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

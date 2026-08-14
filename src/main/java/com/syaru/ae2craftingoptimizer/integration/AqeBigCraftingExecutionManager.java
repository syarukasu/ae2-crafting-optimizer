package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.access.AdvancedAeClusterExecutionAccess;
import com.syaru.ae2craftingoptimizer.access.AdvancedAeExactCraftingJobAccess;
import com.syaru.ae2craftingoptimizer.access.AdvancedAeExactCraftingLogicAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingLogicTransactionAccess;
import com.syaru.ae2craftingoptimizer.api.batch.ExactPatternFormula;
import com.syaru.ae2craftingoptimizer.api.batch.v2.ProviderOwnedPatternBatchTarget;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingHostRegistry;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingHostRuntime;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingRuntime;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchTarget;
import com.syaru.ae2craftingoptimizer.api.vector.ExactVectorDiagnostics;
import com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatch;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.Ae2BigCraftingPlanFactory;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import com.syaru.ae2craftingoptimizer.engine.Ae2CompiledCraftingGraphCache;
import com.syaru.ae2craftingoptimizer.engine.BigCapacityCraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingJob;
import com.syaru.ae2craftingoptimizer.engine.BigKeyCounterSidecars;
import com.syaru.ae2craftingoptimizer.engine.CompiledRootProgram;
import com.syaru.ae2craftingoptimizer.engine.BigIntegerCraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.ExactCraftingJobLedger;
import com.syaru.ae2craftingoptimizer.engine.ExactCraftingJobState;
import com.syaru.ae2craftingoptimizer.engine.PlanningRuntimeEpoch;
import com.syaru.ae2craftingoptimizer.engine.RecipeGenerationTracker;
import com.syaru.ae2craftingoptimizer.engine.craftingtable.PhysicalCraftingTreeTransaction;
import com.syaru.ae2craftingoptimizer.engine.vector.VectorBatchPlanValidator;
import com.syaru.ae2craftingoptimizer.engine.vector.VectorBatchPlanner;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import com.syaru.ae2craftingoptimizer.optimization.ServerTickClock;
import com.syaru.ae2craftingoptimizer.scheduler.PatternProviderRoutingCache;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster;
import net.pedroksl.advanced_ae.common.entities.AdvCraftingBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * AQE BigInteger Jobの実行所有権を管理する。
 *
 * <p>決定的な作業台TreeはACOの永続EscrowとAAC/NeoECOの実Workerで処理し、
 * 対象外だけをchecked-longのAdvanced AE子Jobへ委譲する。</p>
 */
public final class AqeBigCraftingExecutionManager {
    private static final Map<AdvCraftingCPUCluster, Controller> CONTROLLERS = new WeakHashMap<>();

    private AqeBigCraftingExecutionManager() {
    }

    public static synchronized void tick(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        Map<Object, BigCraftingHostRuntime<AEKey>> registered = BigCraftingHostRegistry.snapshot();
        Set<AdvCraftingCPUCluster> liveClusters = new LinkedHashSet<>();
        for (var entry : registered.entrySet()) {
            if (!(entry.getKey() instanceof AdvCraftingCPUCluster cluster)) {
                continue;
            }
            liveClusters.add(cluster);
            Controller current = CONTROLLERS.get(cluster);
            if (current == null || current.host != entry.getValue()) {
                if (current != null) {
                    current.close(true);
                }
                current = new Controller(cluster, entry.getValue());
                CONTROLLERS.put(cluster, current);
            }
            current.tick();
        }
        List<AdvCraftingCPUCluster> stale = CONTROLLERS.keySet().stream()
                .filter(cluster -> !liveClusters.contains(cluster))
                .toList();
        for (AdvCraftingCPUCluster cluster : stale) {
            Controller removed = CONTROLLERS.remove(cluster);
            if (removed != null) {
                removed.close(true);
            }
        }
    }

    public static synchronized int submitAt(
            CommandSourceStack source,
            BlockPos position,
            AEKey output,
            BigInteger amount) {
        Objects.requireNonNull(source, "source");
        if (!ACOConfig.enableBigIntegerGameplayExecution()) {
            source.sendFailure(Component.literal(
                    "ACO BigInteger gameplay execution is disabled in server config."));
            return 0;
        }
        var blockEntity = source.getLevel().getBlockEntity(position);
        if (!(blockEntity instanceof AdvCraftingBlockEntity craftingBlock)) {
            source.sendFailure(Component.literal(
                    "The selected position is not an Advanced AE Quantum Computer block."));
            return 0;
        }
        AdvCraftingCPUCluster cluster = craftingBlock.getCluster();
        if (cluster == null || !cluster.isActive() || cluster.getGrid() == null) {
            source.sendFailure(Component.literal("The selected Quantum Computer is not formed or active."));
            return 0;
        }
        BigCraftingHostRuntime<AEKey> host = BigCraftingHostRegistry.find(cluster).orElse(null);
        if (host == null) {
            source.sendFailure(Component.literal(
                    "This Quantum Computer has no active ACO BigInteger host."));
            return 0;
        }
        Ae2BigCraftingPlanFactory.PreparedBigRootPlan prepared;
        try {
            prepared = Ae2BigCraftingPlanFactory.tryCreate(
                    cluster.getLevel(), cluster.getGrid(), cluster.getSrc(), output, amount);
        } catch (RuntimeException failure) {
            AE2CraftingOptimizer.LOGGER.error("Failed to create AQE BigInteger root plan", failure);
            source.sendFailure(Component.literal("BigInteger plan creation failed: " + failure.getMessage()));
            return 0;
        }
        if (prepared == null) {
            source.sendFailure(Component.literal(
                    "The request is missing, ambiguous, cyclic, fuzzy, or changed during planning; nothing was submitted."));
            return 0;
        }
        BigCraftingJob<AEKey> rootWindowJob = prepared.rootWindowJob();
        // 旧コマンド経路はroot単位子Jobしか実行できないため、Exact専用計画を誤送信しない。
        if (rootWindowJob == null) {
            source.sendFailure(Component.literal(
                    "This request requires an exact-pattern BigInteger CPU executor; the legacy root-window command cannot run it."));
            return 0;
        }
        if (!host.submit(rootWindowJob)) {
            source.sendFailure(Component.literal(
                    "The Quantum Computer does not have enough unreserved BigInteger crafting storage."));
            return 0;
        }
        Controller controller = CONTROLLERS.computeIfAbsent(cluster, ignored -> new Controller(cluster, host));
        cluster.recalculateRemainingStorage();
        cluster.markDirty();
        source.sendSuccess(
                () -> Component.literal(
                        "Submitted ACO BigInteger job "
                                + rootWindowJob.id()
                                + ": "
                                + amount
                                + " x "
                                + output.getDisplayName().getString()
                                + ", reserved "
                                + prepared.reservedBytes()
                                + " bytes"),
                true);
        controller.tick();
        return 1;
    }

    public static synchronized int cancelAt(
            CommandSourceStack source,
            BlockPos position,
            UUID jobId) {
        AdvCraftingCPUCluster cluster = clusterAt(source, position);
        if (cluster == null) {
            return 0;
        }
        BigCraftingHostRuntime<AEKey> host = BigCraftingHostRegistry.find(cluster).orElse(null);
        if (host == null) {
            source.sendFailure(Component.literal("The selected Quantum Computer has no ACO host."));
            return 0;
        }
        Controller controller = CONTROLLERS.computeIfAbsent(cluster, ignored -> new Controller(cluster, host));
        if (!controller.cancel(jobId)) {
            source.sendFailure(Component.literal("Unknown ACO BigInteger job " + jobId));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Cancelled ACO BigInteger job " + jobId), true);
        return 1;
    }

    /**
     * BigInteger状態Menuが保持するHostから、安全なController取消経路を特定する。
     */
    public static synchronized boolean cancel(
            BigCraftingHostRuntime<AEKey> host,
            UUID jobId) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(jobId, "jobId");
        for (var entry : BigCraftingHostRegistry.snapshot().entrySet()) {
            /*
             * 同じRuntimeを所有する形成済みQuantum Computerだけを対象にする。
             * 別CPUの同一UUIDを推測で取り消さない。
             */
            if (entry.getValue() == host
                    && entry.getKey()
                            instanceof AdvCraftingCPUCluster cluster) {
                Controller controller = CONTROLLERS.computeIfAbsent(
                        cluster,
                        ignored -> new Controller(cluster, host));
                return controller.cancel(jobId);
            }
        }
        return false;
    }

    public static synchronized int statusAt(CommandSourceStack source, BlockPos position) {
        AdvCraftingCPUCluster cluster = clusterAt(source, position);
        if (cluster == null) {
            return 0;
        }
        BigCraftingHostRuntime<AEKey> host = BigCraftingHostRegistry.find(cluster).orElse(null);
        if (host == null) {
            source.sendFailure(Component.literal("The selected Quantum Computer has no ACO host."));
            return 0;
        }
        var page = host.statusPage(0, Math.min(64, ACOConfig.getBigIntegerStatusPageEntries()));
        source.sendSuccess(
                () -> Component.literal(
                        "ACO BigInteger CPU: "
                                + page.totalJobs()
                                + " job(s), reserved "
                                + page.reserved()
                                + " / "
                                + page.capacity()),
                false);
        for (var job : page.jobs()) {
            source.sendSuccess(
                    () -> Component.literal(
                            job.id()
                                    + " "
                                    + job.state()
                                    + " remaining="
                                    + job.remainingExecutions()
                                    + " waiting="
                                    + job.waitingAmount()),
                    false);
        }
        return page.jobs().size();
    }

    public static synchronized void onChildFinished(
            AdvCraftingCPUCluster cluster,
            UUID childCpuId,
            boolean successful) {
        if (cluster == null || childCpuId == null) {
            return;
        }
        BigCraftingHostRuntime<AEKey> host = BigCraftingHostRegistry.find(cluster).orElse(null);
        if (host == null) {
            return;
        }
        var binding = host.externalExecutions().get(childCpuId);
        if (binding == null || !host.resolveExternalExecution(childCpuId, successful)) {
            return;
        }
        if (!successful) {
            Controller controller = CONTROLLERS.get(cluster);
            if (controller != null) {
                controller.retryAfter.put(
                        binding.jobId(),
                        ServerTickClock.currentTick() + ACOConfig.getBigIntegerRetryBackoffTicks());
            }
        }
        cluster.recalculateRemainingStorage();
        cluster.markDirty();
    }

    public static synchronized void clear() {
        for (Controller controller : List.copyOf(CONTROLLERS.values())) {
            controller.close(true);
        }
        CONTROLLERS.clear();
        ExactVectorGridTickBudget.clearAll();
    }

    private static AdvCraftingCPUCluster clusterAt(CommandSourceStack source, BlockPos position) {
        var blockEntity = source.getLevel().getBlockEntity(position);
        if (!(blockEntity instanceof AdvCraftingBlockEntity craftingBlock)
                || craftingBlock.getCluster() == null) {
            source.sendFailure(Component.literal(
                    "The selected position is not part of a formed Advanced AE Quantum Computer."));
            return null;
        }
        return craftingBlock.getCluster();
    }

    private static Map<UUID, AdvCraftingCPU> activeCpus(AdvCraftingCPUCluster cluster) {
        return ((AdvancedAeClusterExecutionAccess) (Object) cluster).aco$getActiveCpuSnapshot();
    }

    private static final class Controller {
        private final AdvCraftingCPUCluster cluster;
        private final BigCraftingHostRuntime<AEKey> host;
        private final Map<UUID, PendingCalculation> pending = new HashMap<>();
        private final Map<UUID, Long> retryAfter = new HashMap<>();
        /** BigInteger親Jobごとの正本作業台Tree。AAC側には親会計を持たせない。 */
        private final Map<UUID, PhysicalCraftingTreeTransaction>
                craftingTableTrees = new HashMap<>();
        /** Advanced AE実CPU Jobへ直接付随するBigInteger物理TreeのRuntime cache。 */
        private final Map<UUID, PhysicalCraftingTreeTransaction>
                exactCpuTrees = new HashMap<>();
        /** 複数の実CPU JobへGrid予算を公平に渡す、tick間Round Robin cursor。 */
        private int exactCpuCursor;
        private final ProgramFingerprintRevalidationCache
                revalidatedPrograms =
                        new ProgramFingerprintRevalidationCache();
        private boolean recovered;

        private Controller(
                AdvCraftingCPUCluster cluster,
                BigCraftingHostRuntime<AEKey> host) {
            this.cluster = cluster;
            this.host = host;
        }

        private void tick() {
            recoverOnce();
            pollCalculations();
            if (!cluster.isActive()
                    || cluster.getGrid() == null) {
                return;
            }
            /*
             * 一度物理所有権を取ったExact Jobは、ConfigをOFFにしても安全な完了・取消まで進める。
             * 新規開始だけは各Config判定で止める。
             */
            reconcileExactCpuJobs();
            if (!ACOConfig.enableBigIntegerGameplayExecution()) {
                return;
            }
            reconcileVectorParents();
            Set<UUID> retryableVectorParents =
                    tryStartVectorParents();
            int maximumStarts = Math.max(
                    0,
                    ACOConfig.getBigIntegerMaximumWindowCalculationsPerTick() - pending.size());
            if (maximumStarts == 0) {
                return;
            }
            long window = ACOConfig.getBigIntegerExecutionWindow();
            long budget = Math.multiplyExact(window, (long) maximumStarts);
            List<BigCraftingRuntime.ExecutionLease<AEKey>> leases =
                    host.schedule(budget, maximumStarts);
            for (var lease : leases) {
                /*
                 * AAC設備枠や資源待ちの親Jobは同じExact Vector計画を次tickに再試行する。
                 * ここでchecked-long子Windowへ落とすと数量依存処理が再発するため、Leaseを戻す。
                 */
                if (retryableVectorParents.contains(lease.jobId())) {
                    host.rollback(lease);
                    continue;
                }
                if (!BigCraftingJob.ROOT_WINDOW_TASK_ID.equals(lease.prepared().patternId())) {
                    host.rollback(lease);
                    continue;
                }
                if (isStale(lease)) {
                    host.rollback(lease);
                    host.cancel(lease.jobId());
                    AE2CraftingOptimizer.LOGGER.warn(
                            "Cancelled stale AQE BigInteger job {} after Pattern/recipe generation changed",
                            lease.jobId());
                    continue;
                }
                long retryTick = retryAfter.getOrDefault(lease.jobId(), 0L);
                if (ServerTickClock.currentTick() < retryTick) {
                    host.rollback(lease);
                    continue;
                }
                ICraftingSimulationRequester requester = cluster::getSrc;
                Future<ICraftingPlan> future = cluster.getGrid().getCraftingService()
                        .beginCraftingCalculation(
                                cluster.getLevel(),
                                requester,
                                lease.requestedKey(),
                                lease.prepared().window().executions(),
                                CalculationStrategy.REPORT_MISSING_ITEMS);
                pending.put(lease.jobId(), new PendingCalculation(lease, future));
            }
            cluster.markDirty();
        }

        /**
         * BigInteger JobもAdvanced AEの実CPU、tasks、waitingFor、CraftingLinkを正本として進める。
         */
        private void reconcileExactCpuJobs() {
            List<ExactCpuContext> jobs = orderedExactCpuJobs();
            // Exact Jobがない通常稼働では、クラフトグラフ取得もRuntime cache維持も行わない。
            if (jobs.isEmpty()) {
                exactCpuTrees.clear();
                return;
            }
            boolean hasPhysicalExecution = jobs.stream()
                    .anyMatch(context -> context.state().hasPhysicalExecution());
            /*
             * 新規Exact実行が無効で、復旧すべき物理所有権もない場合は実Jobを待機させる。
             * ここではグラフを走査せず、通常AE2/Advanced AEのCPU表示だけを維持する。
             */
            if (!ACOConfig.enableAqeBigIntegerVectorParents()
                    && !hasPhysicalExecution) {
                exactCpuTrees.clear();
                return;
            }
            IGrid grid = cluster.getGrid();
            var graphSnapshot =
                    Ae2CompiledCraftingGraphCache.getOrCompile(
                            grid,
                            cluster.getLevel());
            Set<UUID> liveExactIds = new LinkedHashSet<>();
            // Runtime cacheには現在も同じExact Jobを所有するCPUだけを残す。
            for (ExactCpuContext context : jobs) {
                liveExactIds.add(context.cpuId());
            }
            exactCpuTrees.keySet().removeIf(id -> !liveExactIds.contains(id));
            int activeTransactions = (int) jobs.stream()
                    .filter(context -> context.state().hasPhysicalExecution())
                    .count();

            for (ExactCpuContext context : jobs) {
                ExactCraftingJobState state = context.state();
                // 会計または物理所有権が不確定なJobは、管理者判断まで自動処理しない。
                if (state.quarantined()) {
                    continue;
                }
                PhysicalCraftingTreeTransaction transaction;
                try {
                    transaction = exactCpuTrees.get(context.cpuId());
                    if (transaction == null && state.hasPhysicalExecution()) {
                        transaction = restoreExactCpuTree(
                                context.cpuId(),
                                state);
                        exactCpuTrees.put(
                                context.cpuId(),
                                transaction);
                    }
                    if (transaction == null) {
                        /*
                         * Config OFF、実行枠不足、またはWorker不在では実CPU Jobを維持する。
                         * 通常Advanced AE executorはExact Jobに触れないため入力は未変更のまま待機する。
                         */
                        if (!ACOConfig.enableAqeBigIntegerVectorParents()
                                || activeTransactions
                                        >= ACOConfig.getExactVectorMaximumActivePerGrid()) {
                            continue;
                        }
                        if (isExactStateStale(
                                state,
                                graphSnapshot)) {
                            finishExactCpu(
                                    context,
                                    false);
                            continue;
                        }
                        PreparedVectorBatch plan =
                                prepareExactCpuVectorPlan(
                                        context.cpuId(),
                                        state,
                                        graphSnapshot);
                        // 対象設備が一時的に存在しない場合は、別経路へ落とさず同じ実Jobで待つ。
                        if (!supportsPhysicalCraftingTablePlan(
                                grid,
                                cluster.getLevel(),
                                graphSnapshot,
                                plan)) {
                            continue;
                        }
                        ExactVectorGridTickBudget startBudget =
                                ExactVectorGridTickBudget.forGrid(grid);
                        // 入力所有権を移す新規Transactionだけが、Grid共有開始枠を消費する。
                        if (!startBudget.tryStart()) {
                            ExactVectorDiagnostics.startBudgetDeferred();
                            continue;
                        }
                        transaction =
                                PhysicalCraftingTreeTransaction.create(
                                        plan,
                                        PhysicalCraftingTreeTransaction.capturePatternAccounting(
                                                plan,
                                                graphSnapshot,
                                                cluster.getLevel()));
                        validateExactCpuPlan(
                                context,
                                transaction.accountingSnapshot(
                                        graphSnapshot,
                                        cluster.getLevel()),
                                graphSnapshot);
                        state.beginPhysicalExecution(
                                transaction.save());
                        exactCpuTrees.put(
                                context.cpuId(),
                                transaction);
                        context.cpu().markDirty();
                        activeTransactions++;
                        ExactVectorDiagnostics.planPrepared();
                        ExactVectorDiagnostics.transactionStarted(
                                com.syaru.ae2craftingoptimizer.api.vector
                                        .VectorResourceMode.NETWORK_STORAGE);
                    }
                } catch (RuntimeException | LinkageError preparationFailure) {
                    /*
                     * 物理Transaction開始前の再構築不一致は入力へ触れていない。
                     * Advanced AE本来の取消通知でJobを閉じ、推測した計画を実行しない。
                     */
                    if (!state.hasPhysicalExecution()) {
                        AE2CraftingOptimizer.LOGGER.error(
                                "Cancelled Advanced AE exact CPU {} before physical ownership because its plan could not be rebuilt",
                                context.cpuId(),
                                preparationFailure);
                        finishExactCpu(
                                context,
                                false);
                        continue;
                    }
                    quarantineExactCpu(
                            context,
                            exactCpuTrees.get(context.cpuId()),
                            "failed to restore exact physical execution: "
                                    + preparationFailure,
                            preparationFailure);
                    continue;
                }

                // 標準CPU画面からの取消要求を、EscrowとWorkerを所有する物理Transactionへ渡す。
                if (state.cancellationRequested()) {
                    transaction.requestCancellation();
                }
                int operationBudget =
                        ExactVectorGridTickBudget.claimActiveStages(
                                grid,
                                Math.max(
                                        1,
                                        transaction.plan()
                                                .craftingSteps()
                                                .size()));
                // 同Gridの時間・段数予算を使い切ったJobはRound Robin順の次tickへ送る。
                if (operationBudget == 0) {
                    continue;
                }
                long tickStartedNanos = System.nanoTime();
                PhysicalCraftingTreeTransaction.TickOutcome outcome;
                try {
                    outcome = transaction.tick(
                            grid,
                            cluster.getLevel(),
                            cluster.getSrc(),
                            graphSnapshot,
                            operationBudget);
                } finally {
                    ExactVectorGridTickBudget.settleActiveStageClaim(
                            grid,
                            operationBudget,
                            transaction.lastConsumedOperations());
                }
                ExactVectorDiagnostics.activeTick(
                        System.nanoTime() - tickStartedNanos);

                try {
                    PhysicalCraftingTreeTransaction.AccountingSnapshot accounting =
                            transaction.accountingSnapshot(
                                    graphSnapshot,
                                    cluster.getLevel());
                    reconcileExactCpuAccounting(
                            context,
                            accounting,
                            graphSnapshot);
                    state.updatePhysicalExecution(
                            transaction.save());
                    context.cpu().markDirty();
                } catch (PhysicalCraftingTreeTransaction.PatternUnavailableException deferred) {
                    // A legacy physical receipt can learn its immutable identity when the provider returns.
                    continue;
                } catch (RuntimeException | LinkageError accountingFailure) {
                    quarantineExactCpu(
                            context,
                            transaction,
                            "Advanced AE exact-job accounting diverged: "
                                    + accountingFailure,
                            accountingFailure);
                    continue;
                }

                if (outcome.kind()
                        == PhysicalCraftingTreeTransaction.Kind.COMPLETE) {
                    /*
                     * 物理完了を理由にカウンタを0へ上書きしない。直前のReceipt反映だけで
                     * 実ExecutingCraftingJob上の
                     * TaskProgress・waitingFor・remainingAmount拡張を直接確認する。
                     */
                    if (!context.exactJob()
                            .aco$isExactAccountingBalanced()) {
                        quarantineExactCpu(
                                context,
                                transaction,
                                "Advanced AE exact job reached COMPLETE with unbalanced runtime counters",
                                null);
                        continue;
                    }
                    exactCpuTrees.remove(
                            context.cpuId());
                    ExactVectorDiagnostics.transactionCompleted();
                    finishExactCpu(
                            context,
                            true);
                } else if (outcome.kind()
                        == PhysicalCraftingTreeTransaction.Kind.CANCELLED) {
                    exactCpuTrees.remove(
                            context.cpuId());
                    ExactVectorDiagnostics.transactionCancelled();
                    finishExactCpu(
                            context,
                            false);
                } else if (outcome.kind()
                        == PhysicalCraftingTreeTransaction.Kind.QUARANTINED) {
                    quarantineExactCpu(
                            context,
                            transaction,
                            outcome.detail(),
                            null);
                }
            }
        }

        private List<ExactCpuContext> orderedExactCpuJobs() {
            List<ExactCpuContext> jobs = new ArrayList<>();
            // activeCpusの全要素から、同じ実ExecutingCraftingJobにExact Sidecarがあるものだけを選ぶ。
            for (var entry : activeCpus(cluster).entrySet()) {
                AdvCraftingCPU cpu = entry.getValue();
                CraftingLogicTransactionAccess logic =
                        (CraftingLogicTransactionAccess) (Object) cpu.craftingLogic;
                Object job = logic.aco$getExecutingJob();
                if (!(job instanceof AdvancedAeExactCraftingJobAccess exactJob)
                        || !exactJob.aco$isExactJob()) {
                    continue;
                }
                ExactCraftingJobState state = exactJob.aco$getExactState();
                if (state == null) {
                    throw new IllegalStateException(
                            "Advanced AE exact job lost its sidecar state");
                }
                jobs.add(new ExactCpuContext(
                        entry.getKey(),
                        cpu,
                        exactJob,
                        state));
            }
            jobs.sort(Comparator.comparing(
                    context -> context.cpuId().toString()));
            if (jobs.isEmpty()) {
                exactCpuCursor = 0;
                return List.of();
            }
            int start = Math.floorMod(
                    exactCpuCursor,
                    jobs.size());
            List<ExactCpuContext> ordered =
                    new ArrayList<>(jobs.size());
            // 保存Cursorから一巡し、一つの巨大Jobが常に先頭でGrid予算を取らないようにする。
            for (int offset = 0; offset < jobs.size(); offset++) {
                ordered.add(jobs.get(
                        Math.floorMod(
                                start + offset,
                                jobs.size())));
            }
            exactCpuCursor = Math.floorMod(
                    start + 1,
                    jobs.size());
            return List.copyOf(ordered);
        }

        private PhysicalCraftingTreeTransaction restoreExactCpuTree(
                UUID cpuId,
                ExactCraftingJobState state) {
            PhysicalCraftingTreeTransaction restored =
                    PhysicalCraftingTreeTransaction.load(
                            state.physicalExecution());
            // CPU UUIDが親Job IDと一致しないNBTを、別の実CPUへ再接続しない。
            if (!restored.plan()
                    .parentJobId()
                    .equals(cpuId)) {
                throw new IllegalArgumentException(
                        "exact physical execution belongs to another Advanced AE CPU");
            }
            return restored;
        }

        private PreparedVectorBatch prepareExactCpuVectorPlan(
                UUID cpuId,
                ExactCraftingJobState state,
                Ae2CompiledCraftingGraphCache.Snapshot graphSnapshot) {
            long currentPatternGeneration =
                    ProviderPatternGenerationTracker.generation();
            long currentRecipeGeneration =
                    RecipeGenerationTracker.generation();
            CompiledRootProgram<AEKey> program =
                    graphSnapshot.rootProgram(
                                    state.requestedKey())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "exact CPU root program is unavailable"));
            // 同じ出力でも別の数式ProgramへJobを付け替えない。
            if (!state.programFingerprint().equals(
                    Ae2BigCraftingPlanFactory.programFingerprint(
                            program))) {
                throw new IllegalArgumentException(
                        "exact CPU root program fingerprint changed");
            }
            int maximumBits =
                    ACOConfig.getBigIntegerMaximumBits();
            /*
             * 提出時に計算した使用在庫だけを再現する。
             * 実行直前の在庫量で計画を作り直すと、task・waitingFor・結果が提出時から変わる。
             */
            CompiledRootProgram.BigInventorySnapshot<AEKey> inventory =
                    program.captureBigInventory(
                            key -> state.plannedInventory()
                                    .getOrDefault(
                                            key,
                                            BigInteger.ZERO),
                            maximumBits);
            PreparedVectorBatch plan = VectorBatchPlanner.prepare(
                    UUID.randomUUID(),
                    cpuId,
                    program,
                    inventory,
                    state.requestedAmount(),
                    state.programFingerprint(),
                    currentPatternGeneration,
                    currentRecipeGeneration,
                    maximumBits);
            VectorBatchPlanValidator.validate(
                    plan,
                    maximumBits,
                    ACOConfig.getExactVectorMaximumPatternNodes(),
                    ACOConfig.getExactVectorMaximumInputKeys(),
                    ACOConfig.getExactVectorMaximumOutputKeys());
            // 計画構築中に世代が変わった結果を物理Targetへ渡さない。
            if (ProviderPatternGenerationTracker.generation()
                            != currentPatternGeneration
                    || RecipeGenerationTracker.generation()
                            != currentRecipeGeneration) {
                throw new IllegalStateException(
                        "exact CPU graph changed while preparing its physical plan");
            }
            return plan;
        }

        private void validateExactCpuPlan(
                ExactCpuContext context,
                PhysicalCraftingTreeTransaction.AccountingSnapshot accounting,
                Ae2CompiledCraftingGraphCache.Snapshot graphSnapshot) {
            Map<AEItemKey, BigInteger> plannedTasks =
                    accounting.plannedPatternDefinitions();
            /*
             * 再構築した物理式は、提出時に実Jobへ載せた全taskと完全一致させる。
             * 確定作業台経路はEmitterを許さないため、初期待機出力も存在してはならない。
             */
            if (!plannedTasks.equals(
                            context.state().taskTotals())
                    || !context.state()
                            .initialWaiting()
                            .isEmpty()) {
                throw new IllegalArgumentException(
                        "physical plan does not match Advanced AE exact-job accounting");
            }
        }

        private void reconcileExactCpuAccounting(
                ExactCpuContext context,
                PhysicalCraftingTreeTransaction.AccountingSnapshot accounting,
                Ae2CompiledCraftingGraphCache.Snapshot graphSnapshot) {
            validateExactCpuPlan(
                    context,
                    accounting,
                    graphSnapshot);
            Map<AEItemKey, BigInteger> dispatchedTasks =
                    accounting.dispatchedPatternDefinitions();
            BigInteger remainingOutput =
                    accounting.finalOutputReturned()
                            ? BigInteger.ZERO
                            : context.state().requestedAmount();
            context.exactJob().aco$reconcileExactAccounting(
                    dispatchedTasks,
                    accounting.introducedOutputs(),
                    accounting.creditedOutputs(),
                    remainingOutput);
            BigInteger exactRemaining =
                    context.exactJob()
                            .aco$getExactRemainingOutput();
            // CPU一覧・クラフト状況へは、実Jobの正確な残数からsigned-long互換投影だけを見せる。
            context.cpu().updateOutput(
                    exactRemaining.signum() == 0
                            ? null
                            : new GenericStack(
                                    context.state().requestedKey(),
                                    ExactCraftingJobLedger.saturatedLong(
                                            exactRemaining)));
        }


        private boolean isExactStateStale(
                ExactCraftingJobState state,
                Ae2CompiledCraftingGraphCache.Snapshot graphSnapshot) {
            long currentPatternGeneration =
                    ProviderPatternGenerationTracker.generation();
            long currentRecipeGeneration =
                    RecipeGenerationTracker.generation();
            // 同一JVM・同一世代なら、提出時の数式Programをそのまま使用できる。
            if (PlanningRuntimeEpoch.current().equals(
                            state.planningEpoch())
                    && state.patternGeneration()
                            == currentPatternGeneration
                    && state.recipeGeneration()
                            == currentRecipeGeneration) {
                return false;
            }
            // 同じ現世代で既に完全一致を証明済みのFingerprintは再走査しない。
            if (revalidatedPrograms.contains(
                    currentPatternGeneration,
                    currentRecipeGeneration,
                    state.programFingerprint())) {
                return false;
            }
            CompiledRootProgram<AEKey> currentProgram =
                    graphSnapshot.rootProgram(
                                    state.requestedKey())
                            .orElse(null);
            boolean matches = currentProgram != null
                    && state.programFingerprint().equals(
                            Ae2BigCraftingPlanFactory.programFingerprint(
                                    currentProgram));
            if (matches) {
                revalidatedPrograms.record(
                        currentPatternGeneration,
                        currentRecipeGeneration,
                        state.programFingerprint());
                ExactVectorDiagnostics.fingerprintRevalidated();
            }
            return !matches;
        }

        private void quarantineExactCpu(
                ExactCpuContext context,
                PhysicalCraftingTreeTransaction transaction,
                String detail,
                Throwable failure) {
            String checkedDetail = detail == null || detail.isBlank()
                    ? "unknown exact-job accounting failure"
                    : detail;
            // 物理所有権が存在する場合は、そのTransaction自身も終端隔離へ固定する。
            if (transaction != null) {
                transaction.quarantineForAccounting(
                        checkedDetail);
                if (context.state().hasPhysicalExecution()) {
                    context.state().updatePhysicalExecution(
                            transaction.save());
                }
            }
            context.state().quarantine();
            context.cpu().markDirty();
            ExactVectorDiagnostics.transactionQuarantined();
            if (failure == null) {
                AE2CraftingOptimizer.LOGGER.error(
                        "Quarantined Advanced AE exact CPU {}: {}",
                        context.cpuId(),
                        checkedDetail);
            } else {
                AE2CraftingOptimizer.LOGGER.error(
                        "Quarantined Advanced AE exact CPU {}: {}",
                        context.cpuId(),
                        checkedDetail,
                        failure);
            }
        }

        private void finishExactCpu(
                ExactCpuContext context,
                boolean successful) {
            /*
             * Private finishJobをInvoker経由で呼び、CraftingLink、owner通知、CPU inventory返却を
             * Advanced AE本来の順序で完了させる。
             */
            ((AdvancedAeExactCraftingLogicAccess) (Object) context.cpu().craftingLogic)
                    .aco$finishExactJob(successful);
            context.cpu().updateOutput(null);
            exactCpuTrees.remove(
                    context.cpuId());
            /*
             * finishJob後はAdvanced AE公開deactivate経路で実CPUをactiveCpusから先に外す。
             * これによりAQEの再計算が終了済みCPUをBigInteger容量へ再予約しない。
             */
            try {
                context.cpu().deactivate();
            } finally {
                // AQE再計算後にも予約が残った場合だけ、同じ実CPU UUIDを冪等に解放する。
                host.releaseExternal(
                        context.cpuId());
            }
            cluster.recalculateRemainingStorage();
            cluster.markDirty();
        }

        private void reconcileVectorParents() {
            if (!ACOConfig.enableAqeBigIntegerVectorParents()) {
                return;
            }
            IGrid grid = cluster.getGrid();
            var graphSnapshot =
                    Ae2CompiledCraftingGraphCache.getOrCompile(
                            grid,
                            cluster.getLevel());
            for (var recovered :
                    List.copyOf(
                            host.unresolvedVectorExecutions())) {
                PhysicalCraftingTreeTransaction transaction;
                try {
                    transaction =
                            craftingTableTrees.computeIfAbsent(
                                    recovered.jobId(),
                                    ignored ->
                                            restoreCraftingTableTree(
                                                    recovered));
                } catch (RuntimeException | LinkageError invalidState) {
                    host.quarantineVector(
                            recovered.jobId(),
                            recovered.prepared()
                                    .transactionId());
                    cluster.recalculateRemainingStorage();
                    cluster.markDirty();
                    AE2CraftingOptimizer.LOGGER.error(
                            "Quarantined AQE crafting-table parent job {} because its saved state is invalid",
                            recovered.jobId(),
                            invalidState);
                    continue;
                }

                /*
                 * 全固有Patternを同tickに並列投入できる範囲をGrid共有予算から取得する。
                 * 注文数量は要求件数へ一切使わない。
                 */
                int operationBudget =
                        ExactVectorGridTickBudget
                                .claimActiveStages(
                                        grid,
                                        Math.max(
                                                1,
                                                transaction.plan()
                                                        .craftingSteps()
                                                        .size()));
                if (operationBudget == 0) {
                    break;
                }
                long tickStartedNanos =
                        System.nanoTime();
                PhysicalCraftingTreeTransaction.TickOutcome outcome;
                try {
                    outcome =
                            transaction.tick(
                                    grid,
                                    cluster.getLevel(),
                                    cluster.getSrc(),
                                    graphSnapshot,
                                    operationBudget);
                } finally {
                    /*
                     * 依存入力待ちなどで設備処理しなかった段は、同じGridの後続Jobへ返す。
                     * Transaction側の実消費数は必ずClaim以下に保たれる。
                     */
                    ExactVectorGridTickBudget
                            .settleActiveStageClaim(
                                    grid,
                                    operationBudget,
                                    transaction.lastConsumedOperations());
                }
                ExactVectorDiagnostics.activeTick(
                        System.nanoTime()
                                - tickStartedNanos);
                /*
                 * 物理Thread進捗を含む親状態を先に保存する。
                 * 最終commit、rollback、quarantineはその後にだけ実行する。
                 */
                host.updateVector(
                        recovered.jobId(),
                        recovered.prepared()
                                .transactionId(),
                        transaction.save(),
                        transaction.progressNumerator(),
                        transaction.progressDenominator());
                cluster.markDirty();

                if (outcome.kind()
                        == PhysicalCraftingTreeTransaction.Kind.COMPLETE) {
                    host.commitVector(
                            recovered.jobId(),
                            recovered.prepared()
                                    .transactionId());
                    craftingTableTrees.remove(
                            recovered.jobId());
                    ExactVectorDiagnostics.transactionCompleted();
                    cluster.recalculateRemainingStorage();
                    cluster.markDirty();
                } else if (outcome.kind()
                        == PhysicalCraftingTreeTransaction.Kind.CANCELLED) {
                    host.rollbackVector(
                            recovered.jobId(),
                            recovered.prepared()
                                    .transactionId());
                    host.cancel(
                            recovered.jobId());
                    craftingTableTrees.remove(
                            recovered.jobId());
                    ExactVectorDiagnostics.transactionCancelled();
                    cluster.recalculateRemainingStorage();
                    cluster.markDirty();
                } else if (outcome.kind()
                        == PhysicalCraftingTreeTransaction.Kind.QUARANTINED) {
                    host.quarantineVector(
                            recovered.jobId(),
                            recovered.prepared()
                                    .transactionId());
                    ExactVectorDiagnostics.transactionQuarantined();
                    cluster.recalculateRemainingStorage();
                    cluster.markDirty();
                    AE2CraftingOptimizer.LOGGER.error(
                            "Quarantined AQE crafting-table parent job {}: {}",
                            recovered.jobId(),
                            outcome.detail());
                }
            }
        }

        private Set<UUID> tryStartVectorParents() {
            Set<UUID> retryableParents = new LinkedHashSet<>();
            if (!ACOConfig.enableAqeBigIntegerVectorParents()) {
                return Set.of();
            }
            IGrid grid = cluster.getGrid();
            ExactVectorGridTickBudget budget =
                    ExactVectorGridTickBudget.forGrid(grid);
            int activeTransactions =
                    host.unresolvedVectorExecutions()
                            .size();
            if (activeTransactions
                    >= ACOConfig.getExactVectorMaximumActivePerGrid()) {
                // 親Job枠が空くまで、適格な親をchecked-long子Windowへ落とさない。
                for (BigCraftingRuntime.VectorCandidate<AEKey> candidate :
                        host.vectorCandidates()) {
                    retryableParents.add(
                            candidate.jobId());
                }
                return Set.copyOf(retryableParents);
            }

            var graphSnapshot =
                    Ae2CompiledCraftingGraphCache.getOrCompile(
                            grid,
                            cluster.getLevel());
            for (BigCraftingRuntime.VectorCandidate<AEKey> candidate :
                    host.vectorCandidates()) {
                if (activeTransactions
                                >= ACOConfig
                                        .getExactVectorMaximumActivePerGrid()) {
                    retryableParents.add(candidate.jobId());
                    continue;
                }
                PreparedVectorBatch plan =
                        prepareVectorPlan(candidate);
                if (plan == null) {
                    continue;
                }
                /*
                 * 入力抽出より前に、計画内の全Patternが実作業台式であり、
                 * 少なくとも一つのAAC/NeoECO Pattern Busが所有することを証明する。
                 */
                if (!supportsPhysicalCraftingTablePlan(
                        grid,
                        cluster.getLevel(),
                        graphSnapshot,
                        plan)) {
                    /*
                     * AAC/NeoECO Targetが存在しない環境ではOptional高速経路を所有せず、
                     * 標準checked-long子Windowへ進ませる。
                     */
                    continue;
                }
                ExactVectorDiagnostics.planPrepared();
                // 実際に親Receiptを保存できる候補だけが、このtickの開始予算を消費する。
                if (!budget.tryStart()) {
                    ExactVectorDiagnostics.startBudgetDeferred();
                    retryableParents.add(candidate.jobId());
                    continue;
                }

                try {
                    PhysicalCraftingTreeTransaction transaction =
                            PhysicalCraftingTreeTransaction.create(
                                    plan,
                                    PhysicalCraftingTreeTransaction.capturePatternAccounting(
                                            plan,
                                            graphSnapshot,
                                            cluster.getLevel()));
                    host.prepareVector(
                            candidate.jobId(),
                            plan.transactionId(),
                            PhysicalCraftingTreeTransaction.ENGINE_ID,
                            plan.programFingerprint(),
                            transaction.save(),
                            transaction.progressNumerator(),
                            transaction.progressDenominator());
                    craftingTableTrees.put(
                            candidate.jobId(),
                            transaction);
                    ExactVectorDiagnostics.transactionStarted(
                            com.syaru.ae2craftingoptimizer.api.vector
                                    .VectorResourceMode.NETWORK_STORAGE);
                } catch (RuntimeException | LinkageError preparationFailure) {
                    // 入力抽出前なので、prepare不能な候補は次tickへ延期する。
                    AE2CraftingOptimizer.LOGGER.error(
                            "Failed to prepare AQE crafting-table parent job {}",
                            candidate.jobId(),
                            preparationFailure);
                    retryableParents.add(candidate.jobId());
                    continue;
                }
                cluster.markDirty();
                activeTransactions++;
                if (ACOConfig.logExactVectorDiagnostics()) {
                    AE2CraftingOptimizer.LOGGER.info(
                            "Prepared AQE crafting-table parent job {}: {} logical execution(s), {} physical recipe step(s)",
                            candidate.jobId(),
                            plan.logicalExecutions(),
                            plan.craftingSteps()
                                    .size());
                }
            }
            return Set.copyOf(retryableParents);
        }

        private PhysicalCraftingTreeTransaction restoreCraftingTableTree(
                BigCraftingRuntime.RecoveredVectorExecution<AEKey>
                        recovered) {
            // 新実行器ID以外の旧AAC Direct Receiptは、同じものとして再開しない。
            if (!PhysicalCraftingTreeTransaction.ENGINE_ID.equals(
                    recovered.prepared()
                            .executorId())) {
                throw new IllegalArgumentException(
                        "legacy direct-vector executor state cannot be resumed");
            }
            PhysicalCraftingTreeTransaction restored =
                    PhysicalCraftingTreeTransaction.load(
                            recovered.prepared()
                                    .executionState());
            // 親JobとTransaction UUIDの二重照合で、別JobのNBT取り違えを拒否する。
            if (!restored.plan()
                            .parentJobId()
                            .equals(
                                    recovered.jobId())
                    || !restored.transactionId()
                            .equals(
                                    recovered.prepared()
                                            .transactionId())) {
                throw new IllegalArgumentException(
                        "crafting-table tree state belongs to another parent job");
            }
            return restored;
        }

        private boolean supportsPhysicalCraftingTablePlan(
                IGrid grid,
                net.minecraft.world.level.Level level,
                Ae2CompiledCraftingGraphCache.Snapshot snapshot,
                PreparedVectorBatch plan) {
            // ACOの世代付きProvider経路を使えないCrafting Serviceは安全な対象外とする。
            if (!(grid.getCraftingService()
                    instanceof CraftingService service)) {
                return false;
            }
            // 全固有Patternについて、作業台式とAAC/NeoECO所有Providerを開始前に証明する。
            for (var step :
                    plan.craftingSteps()) {
                IPatternDetails pattern =
                        snapshot.pattern(
                                step.patternId());
                if (pattern == null
                        || ExactPatternFormula.tryCreate(
                                        pattern,
                                        level,
                                        step.selectedInputs())
                                .isEmpty()) {
                    return false;
                }
                boolean found = false;
                // Provider候補数だけを一巡し、永続物理Targetを一件以上要求する。
                for (ICraftingProvider provider :
                        PatternProviderRoutingCache.candidates(
                                service,
                                pattern)) {
                    if (!(provider
                            instanceof ProviderOwnedPatternBatchTarget owned)) {
                        continue;
                    }
                    BlockEntity target =
                            owned.aco$getProviderOwnedBatchTarget();
                    if (target
                            instanceof CraftingTableBatchTarget) {
                        found = true;
                        break;
                    }
                }
                // 一段でも実機所有者がなければ、境界入力へ触る前に待機させる。
                if (!found) {
                    return false;
                }
            }
            return true;
        }

        private PreparedVectorBatch prepareVectorPlan(
                BigCraftingRuntime.VectorCandidate<AEKey> candidate) {
            try {
                long currentPatternGeneration =
                        ProviderPatternGenerationTracker.generation();
                long currentRecipeGeneration =
                        RecipeGenerationTracker.generation();
                var graphSnapshot =
                        Ae2CompiledCraftingGraphCache.getOrCompile(
                                cluster.getGrid(), cluster.getLevel());
                CompiledRootProgram<AEKey> program =
                        graphSnapshot.rootProgram(
                                        candidate.requestedKey())
                                .orElse(null);
                if (program == null
                        || graphSnapshot.graph().generation()
                                != currentPatternGeneration
                        || graphSnapshot.recipeGeneration()
                                != currentRecipeGeneration
                        || !candidate.programFingerprint().equals(
                                Ae2BigCraftingPlanFactory
                                        .programFingerprint(program))) {
                    return null;
                }
                BigKeyCounterSidecars.Snapshot exact =
                        BigKeyCounterSidecars.snapshot(
                                        cluster.getGrid()
                                                .getStorageService()
                                                .getCachedInventory())
                                .orElse(null);
                // 不完全なSidecarをLong.MAX_VALUEの在庫として採用しない。
                if (exact == null || !exact.complete()) {
                    return null;
                }
                int maximumBits =
                        ACOConfig.getBigIntegerMaximumBits();
                CompiledRootProgram.BigInventorySnapshot<AEKey>
                        inventory = program.captureBigInventory(
                                key -> key.equals(
                                                candidate.requestedKey())
                                        ? BigInteger.ZERO
                                        : exact.amount(key),
                                maximumBits);
                PreparedVectorBatch plan = VectorBatchPlanner.prepare(
                        UUID.randomUUID(),
                        candidate.jobId(),
                        program,
                        inventory,
                        candidate.requestedAmount(),
                        candidate.programFingerprint(),
                        currentPatternGeneration,
                        currentRecipeGeneration,
                        maximumBits);
                VectorBatchPlanValidator.validate(
                        plan,
                        maximumBits,
                        ACOConfig
                                .getExactVectorMaximumPatternNodes(),
                        ACOConfig.getExactVectorMaximumInputKeys(),
                        ACOConfig.getExactVectorMaximumOutputKeys());
                // 計画直後に世代が変わった結果は、Executorへsimulateすら渡さない。
                if (ProviderPatternGenerationTracker.generation()
                                != currentPatternGeneration
                        || RecipeGenerationTracker.generation()
                                != currentRecipeGeneration) {
                    return null;
                }
                return plan;
            } catch (RuntimeException | LinkageError unsupported) {
                if (ACOConfig.logExactVectorDiagnostics()) {
                    AE2CraftingOptimizer.LOGGER.debug(
                            "AQE parent job {} is not eligible for Exact Vector execution: {}",
                            candidate.jobId(),
                            unsupported.toString());
                }
                return null;
            }
        }

        private void recoverOnce() {
            if (recovered) {
                return;
            }
            recovered = true;
            int rolledBack = host.rollbackUnboundPreparedExecutions();
            if (rolledBack > 0) {
                AE2CraftingOptimizer.LOGGER.warn(
                        "Rolled back {} AQE BigInteger calculation lease(s) that had no child CPU ownership",
                        rolledBack);
            }
            Set<UUID> active = activeCpus(cluster).keySet();
            for (UUID childId : List.copyOf(host.managedExternalChildIds())) {
                if (!active.contains(childId) && host.quarantineExternalExecution(childId)) {
                    AE2CraftingOptimizer.LOGGER.error(
                            "Quarantined AQE BigInteger execution because child CPU {} is missing",
                            childId);
                }
            }
            cluster.recalculateRemainingStorage();
            cluster.markDirty();
        }

        private void pollCalculations() {
            for (PendingCalculation calculation : new ArrayList<>(pending.values())) {
                if (!calculation.future().isDone()) {
                    continue;
                }
                pending.remove(calculation.lease().jobId());
                try {
                    ICraftingPlan plan = calculation.future().get();
                    if (!validPlan(calculation.lease(), plan)) {
                        failCalculation(calculation, "calculation returned a missing or incompatible plan");
                        continue;
                    }
                    submitChild(calculation.lease(), plan);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    failCalculation(calculation, "calculation thread was interrupted");
                } catch (ExecutionException | RuntimeException failure) {
                    AE2CraftingOptimizer.LOGGER.error(
                            "AQE BigInteger child calculation failed for job {}",
                            calculation.lease().jobId(),
                            failure);
                    failCalculation(calculation, "calculation failed");
                }
            }
        }

        private boolean validPlan(
                BigCraftingRuntime.ExecutionLease<AEKey> lease,
                ICraftingPlan plan) {
            BigInteger childReserved = exactChildCapacity(plan);
            if (plan == null
                    || Ae2CraftingPlanSidecars.bigInteger(plan).isPresent()
                    || plan.simulation()
                    || plan.bytes() <= 0L
                    || childReserved == null
                    || childReserved.compareTo(lease.jobReservedCapacity()) > 0
                    || plan.finalOutput() == null
                    || !lease.requestedKey().equals(plan.finalOutput().what())
                    || plan.finalOutput().amount() != lease.prepared().window().executions()
                    || !plan.missingItems().isEmpty()) {
                return false;
            }
            return !isStale(lease);
        }

        private void submitChild(
                BigCraftingRuntime.ExecutionLease<AEKey> lease,
                ICraftingPlan plan) {
            Map<UUID, AdvCraftingCPU> before = activeCpus(cluster);
            BigInteger childReserved = exactChildCapacity(plan);
            // validPlan後も真値を再取得し、容量メタデータを失ったPlanをAdvanced AEへ渡さない。
            if (childReserved == null) {
                throw new IllegalStateException("child plan lost its exact capacity metadata");
            }
            var result = AqeBigCraftingExecutionContext.withAllowance(
                    cluster,
                    plan.bytes(),
                    childReserved,
                    () -> cluster.submitJob(
                            cluster.getGrid(), plan, cluster.getSrc(), null));
            Map<UUID, AdvCraftingCPU> after = activeCpus(cluster);
            Set<UUID> added = new LinkedHashSet<>(after.keySet());
            added.removeAll(before.keySet());
            if (!result.successful() || added.size() != 1) {
                for (UUID childId : added) {
                    cluster.cancelJob(childId);
                }
                host.rollback(lease);
                retryAfter.put(
                        lease.jobId(),
                        ServerTickClock.currentTick() + ACOConfig.getBigIntegerRetryBackoffTicks());
                AE2CraftingOptimizer.LOGGER.warn(
                        "AQE rejected BigInteger child window for job {}: success={}, newCpuCount={}, error={}",
                        lease.jobId(),
                        result.successful(),
                        added.size(),
                        result.errorCode());
                cluster.recalculateRemainingStorage();
                cluster.markDirty();
                return;
            }
            UUID childId = added.iterator().next();
            try {
                // 親Jobの予約内にある正確な子容量をBindingへ移し、通常予約との二重計上を除く。
                host.bindExternalExecution(
                        lease, childId, childReserved);
            } catch (RuntimeException failure) {
                cluster.cancelJob(childId);
                host.rollback(lease);
                cluster.recalculateRemainingStorage();
                cluster.markDirty();
                throw failure;
            }
            cluster.recalculateRemainingStorage();
            cluster.markDirty();
        }

        private BigInteger exactChildCapacity(ICraftingPlan plan) {
            // 容量だけlongを超える子PlanはSidecarの真値をBindingへ保存する。
            BigCapacityCraftingPlan bigCapacityPlan =
                    Ae2CraftingPlanSidecars.bigCapacity(plan).orElse(null);
            if (bigCapacityPlan != null) {
                return bigCapacityPlan.exactBytes();
            }
            // 個別カウンタまでlongを超える親Planは、子Windowとして再帰提出しない。
            if (plan == null
                    || Ae2CraftingPlanSidecars.bigInteger(plan).isPresent()
                    || plan.bytes() <= 0L) {
                return null;
            }
            return BigInteger.valueOf(plan.bytes());
        }

        private void failCalculation(PendingCalculation calculation, String reason) {
            try {
                host.rollback(calculation.lease());
            } catch (RuntimeException failure) {
                AE2CraftingOptimizer.LOGGER.error(
                        "Failed to roll back AQE BigInteger calculation lease {}",
                        calculation.lease().prepared().transactionId(),
                        failure);
            }
            retryAfter.put(
                    calculation.lease().jobId(),
                    ServerTickClock.currentTick() + ACOConfig.getBigIntegerRetryBackoffTicks());
            AE2CraftingOptimizer.LOGGER.debug(
                    "Deferred AQE BigInteger job {}: {}",
                    calculation.lease().jobId(),
                    reason);
            cluster.markDirty();
        }

        private boolean isStale(BigCraftingRuntime.ExecutionLease<AEKey> lease) {
            long currentPatternGeneration = ProviderPatternGenerationTracker.generation();
            long currentRecipeGeneration = RecipeGenerationTracker.generation();
            // 世代が一致する同一JVM Jobは、再コンパイルせずそのまま継続できる。
            if (PlanningRuntimeEpoch.current().equals(lease.planningEpoch())
                    && lease.patternGeneration() == currentPatternGeneration
                    && lease.recipeGeneration() == currentRecipeGeneration) {
                return false;
            }
            /*
             * Provider世代はGrid全体であり、無関係なPattern Busの追加・解除でも変化する。
             * 保存したroot数式Fingerprintがない旧Schemaだけは、安全な再検証ができない。
             */
            if (lease.planningEpoch().isEmpty() || lease.programFingerprint().isEmpty()) {
                return true;
            }
            /*
             * 同じ現在世代で一度完全一致を証明したProgramは、次の世代変更まで再コンパイルしない。
             * 指紋はroot、AEKey、出力量、入力辺、Emitter状態を含む。
             */
            if (revalidatedPrograms.contains(
                    currentPatternGeneration,
                    currentRecipeGeneration,
                    lease.programFingerprint())) {
                return false;
            }
            try {
                var snapshot = Ae2CompiledCraftingGraphCache.getOrCompile(
                        cluster.getGrid(), cluster.getLevel());
                var currentProgram = snapshot.rootProgram(lease.requestedKey()).orElse(null);
                /*
                 * 再起動または無関係なProvider更新で世代番号だけが変わった場合は、
                 * 正規化Fingerprintが完全一致する同じ決定的Programだけを継続する。
                 */
                boolean matches = currentProgram != null
                        && lease.programFingerprint().equals(
                                Ae2BigCraftingPlanFactory.programFingerprint(currentProgram));
                if (matches) {
                    revalidatedPrograms.record(
                            currentPatternGeneration,
                            currentRecipeGeneration,
                            lease.programFingerprint());
                    ExactVectorDiagnostics.fingerprintRevalidated();
                }
                return !matches;
            } catch (RuntimeException invalidCurrentGraph) {
                // 再構築不能・世代競合時はJobを進めず、呼出側が安全に取消する。
                return true;
            }
        }

        private boolean cancel(UUID jobId) {
            PendingCalculation calculation = pending.remove(jobId);
            if (calculation != null) {
                calculation.future().cancel(true);
                host.rollback(calculation.lease());
            }
            var vector = host.unresolvedVectorExecutions().stream()
                    .filter(recovered ->
                            recovered.jobId().equals(jobId))
                    .findFirst()
                    .orElse(null);
            if (vector != null) {
                try {
                    PhysicalCraftingTreeTransaction transaction =
                            craftingTableTrees.computeIfAbsent(
                                    jobId,
                                    ignored ->
                                            restoreCraftingTableTree(
                                                    vector));
                    // 出力挿入開始後は完全Rollback不能なので、取消要求を明示的に拒否する。
                    if (!transaction.requestCancellation()) {
                        return false;
                    }
                    host.updateVector(
                            jobId,
                            vector.prepared()
                                    .transactionId(),
                            transaction.save(),
                            transaction.progressNumerator(),
                            transaction.progressDenominator());
                    cluster.markDirty();
                    return true;
                } catch (RuntimeException | LinkageError cancellationFailure) {
                    host.quarantineVector(
                            jobId,
                            vector.prepared().transactionId());
                    cluster.recalculateRemainingStorage();
                    cluster.markDirty();
                    AE2CraftingOptimizer.LOGGER.error(
                            "AQE Exact Vector cancellation became uncertain for job {}",
                            jobId,
                            cancellationFailure);
                    return true;
                }
            }
            List<UUID> children = host.externalExecutions().values().stream()
                    .filter(binding -> binding.jobId().equals(jobId))
                    .map(BigCraftingHostRuntime.ExternalExecutionBinding::childCpuId)
                    .toList();
            for (UUID childId : children) {
                cluster.cancelJob(childId);
                host.resolveExternalExecution(childId, false);
            }
            boolean cancelled = host.cancel(jobId);
            if (calculation != null || !children.isEmpty() || cancelled) {
                cluster.recalculateRemainingStorage();
                cluster.markDirty();
                return true;
            }
            return false;
        }

        private void close(boolean rollbackPending) {
            for (PendingCalculation calculation : List.copyOf(pending.values())) {
                calculation.future().cancel(true);
                if (rollbackPending) {
                    try {
                        host.rollback(calculation.lease());
                    } catch (RuntimeException failure) {
                        AE2CraftingOptimizer.LOGGER.error(
                                "Failed to roll back pending AQE BigInteger calculation during shutdown",
                                failure);
                    }
                }
            }
            pending.clear();
            exactCpuTrees.clear();
            if (rollbackPending) {
                cluster.recalculateRemainingStorage();
                cluster.markDirty();
            }
        }
    }

    private record ExactCpuContext(
            UUID cpuId,
            AdvCraftingCPU cpu,
            AdvancedAeExactCraftingJobAccess exactJob,
            ExactCraftingJobState state) {
        private ExactCpuContext {
            Objects.requireNonNull(cpuId, "cpuId");
            Objects.requireNonNull(cpu, "cpu");
            Objects.requireNonNull(exactJob, "exactJob");
            Objects.requireNonNull(state, "state");
        }
    }

    private record PendingCalculation(
            BigCraftingRuntime.ExecutionLease<AEKey> lease,
            Future<ICraftingPlan> future) {
        private PendingCalculation {
            Objects.requireNonNull(lease, "lease");
            Objects.requireNonNull(future, "future");
        }
    }

}

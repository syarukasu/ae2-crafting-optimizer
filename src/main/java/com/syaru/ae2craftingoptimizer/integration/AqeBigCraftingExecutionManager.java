package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.access.AdvancedAeClusterExecutionAccess;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingHostRegistry;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingHostRuntime;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingRuntime;
import com.syaru.ae2craftingoptimizer.api.vector.ExactVectorExecutor;
import com.syaru.ae2craftingoptimizer.api.vector.ExactVectorExecutorRegistry;
import com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatch;
import com.syaru.ae2craftingoptimizer.api.vector.VectorExecutionOffer;
import com.syaru.ae2craftingoptimizer.api.vector.VectorStartResult;
import com.syaru.ae2craftingoptimizer.api.vector.VectorTransactionSnapshot;
import com.syaru.ae2craftingoptimizer.api.vector.VectorTransactionStatus;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.Ae2BigCraftingPlanFactory;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import com.syaru.ae2craftingoptimizer.engine.Ae2CompiledCraftingGraphCache;
import com.syaru.ae2craftingoptimizer.engine.BigCapacityCraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingJob;
import com.syaru.ae2craftingoptimizer.engine.BigKeyCounterSidecars;
import com.syaru.ae2craftingoptimizer.engine.CompiledRootProgram;
import com.syaru.ae2craftingoptimizer.engine.BigIntegerCraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.PlanningRuntimeEpoch;
import com.syaru.ae2craftingoptimizer.engine.RecipeGenerationTracker;
import com.syaru.ae2craftingoptimizer.engine.vector.VectorBatchPlanValidator;
import com.syaru.ae2craftingoptimizer.engine.vector.VectorBatchPlanner;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import com.syaru.ae2craftingoptimizer.optimization.ServerTickClock;
import java.math.BigInteger;
import java.util.ArrayList;
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

/**
 * AQE BigInteger Jobを標準Advanced AE Jobへ一窓ずつ委譲する。
 * Pattern投入・初期素材抽出・出力待ちはAdvanced AE本体が担当し、ACOは窓の所有権と公平順序だけを管理する。
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
        if (!host.submit(prepared.job())) {
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
                                + prepared.job().id()
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
            if (!ACOConfig.enableBigIntegerGameplayExecution()
                    || !cluster.isActive()
                    || cluster.getGrid() == null) {
                return;
            }
            reconcileVectorParents();
            tryStartVectorParents();
            // Vector専用運用では、未受理親Jobをchecked-long子Windowへ自動移行しない。
            if (ACOConfig.enableAqeBigIntegerVectorParents()
                    && !ACOConfig.exactVectorFallbackBeforeOwnershipTransfer()) {
                return;
            }
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

        private void reconcileVectorParents() {
            if (!ACOConfig.enableAqeBigIntegerVectorParents()) {
                return;
            }
            IGrid grid = cluster.getGrid();
            List<ExactVectorExecutor> executors =
                    ExactVectorExecutorRegistry.findRegistered(grid);
            if (executors.isEmpty()) {
                // 設備チャンクが未読込でも親Leaseを推測で戻さず、再登録を待つ。
                return;
            }
            ExactVectorGridTickBudget budget =
                    ExactVectorGridTickBudget.forGrid(grid);
            for (var recovered :
                    List.copyOf(host.unresolvedVectorExecutions())) {
                ExactVectorExecutor executor = executors.stream()
                        .filter(candidate -> candidate.identity().id().equals(
                                recovered.prepared().executorId()))
                        .findFirst()
                        .orElse(null);
                // 保存Receiptを所有する設備がまだ形成されていない場合は、そのまま待機する。
                if (executor == null) {
                    continue;
                }
                Optional<VectorTransactionSnapshot> snapshot;
                try {
                    snapshot = executor.snapshot(
                            recovered.prepared().transactionId());
                } catch (RuntimeException | LinkageError snapshotFailure) {
                    /*
                     * Executorは読込済みなのにReceiptを照合できない。
                     * 親Leaseを戻すと同じ入力を再利用し得るため隔離する。
                     */
                    host.quarantineVector(
                            recovered.jobId(),
                            recovered.prepared().transactionId());
                    cluster.recalculateRemainingStorage();
                    cluster.markDirty();
                    AE2CraftingOptimizer.LOGGER.error(
                            "Failed to read AQE Exact Vector parent receipt for job {}",
                            recovered.jobId(),
                            snapshotFailure);
                    continue;
                }
                if (snapshot.isEmpty()) {
                    // 同一設備が読込済みなのにReceiptが無ければ、start前に止まったLeaseで入力所有権はない。
                    host.rollbackVector(
                            recovered.jobId(),
                            recovered.prepared().transactionId());
                    cluster.markDirty();
                    continue;
                }
                VectorTransactionStatus status =
                        snapshot.get().status();
                if ((status == VectorTransactionStatus.ACCOUNTING
                                || status == VectorTransactionStatus.COMPLETED)
                        && budget.tryCompletion()) {
                    /*
                     * Receiptを先にCOMPLETEDへする。ここで停止しても次回はCOMPLETEDから
                     * 親commitを再実行でき、出力を再挿入する経路には戻らない。
                     */
                    boolean executorCompleted = true;
                    try {
                        if (status == VectorTransactionStatus.ACCOUNTING) {
                            executorCompleted = executor.completeAccounting(
                                    recovered.prepared().transactionId());
                        }
                    } catch (RuntimeException | LinkageError completionFailure) {
                        executorCompleted = false;
                        AE2CraftingOptimizer.LOGGER.error(
                                "AQE Exact Vector executor accounting acknowledgement failed for job {}",
                                recovered.jobId(),
                                completionFailure);
                    }
                    if (!executorCompleted) {
                        host.quarantineVector(
                                recovered.jobId(),
                                recovered.prepared().transactionId());
                        cluster.recalculateRemainingStorage();
                        cluster.markDirty();
                        continue;
                    }
                    try {
                        host.commitVector(
                                recovered.jobId(),
                                recovered.prepared().transactionId());
                    } catch (RuntimeException commitFailure) {
                        /*
                         * Executor側はCOMPLETEDでも親commitの成否が不明なため、
                         * 同じ出力を再会計せず親Jobを隔離する。
                         */
                        host.quarantineVector(
                                recovered.jobId(),
                                recovered.prepared().transactionId());
                        cluster.recalculateRemainingStorage();
                        cluster.markDirty();
                        AE2CraftingOptimizer.LOGGER.error(
                                "AQE Exact Vector parent commit became uncertain for job {}",
                                recovered.jobId(),
                                commitFailure);
                        continue;
                    }
                    cluster.recalculateRemainingStorage();
                    cluster.markDirty();
                    if (ACOConfig.logExactVectorDiagnostics()) {
                        AE2CraftingOptimizer.LOGGER.info(
                                "Committed AQE Exact Vector parent job {} via {}",
                                recovered.jobId(),
                                recovered.prepared().executorId());
                    }
                    continue;
                }
                if (status == VectorTransactionStatus.CANCELLED) {
                    host.rollbackVector(
                            recovered.jobId(),
                            recovered.prepared().transactionId());
                    retryAfter.put(
                            recovered.jobId(),
                            ServerTickClock.currentTick()
                                    + ACOConfig
                                            .getBigIntegerRetryBackoffTicks());
                    cluster.markDirty();
                } else if (status
                        == VectorTransactionStatus.QUARANTINED) {
                    host.quarantineVector(
                            recovered.jobId(),
                            recovered.prepared().transactionId());
                    cluster.recalculateRemainingStorage();
                    cluster.markDirty();
                    AE2CraftingOptimizer.LOGGER.error(
                            "Quarantined AQE Exact Vector parent job {}: {}",
                            recovered.jobId(),
                            snapshot.get().detail());
                }
            }
        }

        private void tryStartVectorParents() {
            if (!ACOConfig.enableAqeBigIntegerVectorParents()) {
                return;
            }
            IGrid grid = cluster.getGrid();
            List<ExactVectorExecutor> executors =
                    ExactVectorExecutorRegistry.find(grid);
            if (executors.isEmpty()) {
                return;
            }
            ExactVectorGridTickBudget budget =
                    ExactVectorGridTickBudget.forGrid(grid);
            int activeTransactions =
                    ExactVectorExecutorRegistry.activeTransactionCount(grid);
            if (activeTransactions
                    >= ACOConfig.getExactVectorMaximumActivePerGrid()) {
                return;
            }

            for (BigCraftingRuntime.VectorCandidate<AEKey> candidate :
                    host.vectorCandidates()) {
                if (activeTransactions
                                >= ACOConfig
                                        .getExactVectorMaximumActivePerGrid()) {
                    return;
                }
                if (candidate.requestedAmount().compareTo(
                                BigInteger.valueOf(
                                        ACOConfig
                                                .getExactVectorMinimumExecutions()))
                        < 0) {
                    continue;
                }
                PreparedVectorBatch plan =
                        prepareVectorPlan(candidate);
                if (plan == null) {
                    continue;
                }
                ExactVectorExecutor selected = null;
                for (ExactVectorExecutor executor : executors) {
                    VectorExecutionOffer offer;
                    try {
                        offer = executor.simulate(plan);
                    } catch (RuntimeException | LinkageError failure) {
                        AE2CraftingOptimizer.LOGGER.warn(
                                "Exact Vector executor {} failed simulation",
                                executor.identity().id(),
                                failure);
                        continue;
                    }
                    if (offer.accepted()
                            && offer.durationTicks()
                                    == plan.durationTicks()
                            && offer.physicalThreadSlots() > 0) {
                        selected = executor;
                        break;
                    }
                }
                if (selected == null) {
                    continue;
                }
                // 実際に外部Receiptを作れる候補だけが、このtickの開始予算を消費する。
                if (!budget.tryStart()) {
                    return;
                }

                String selectedExecutorId;
                try {
                    selectedExecutorId = selected.identity().id();
                    host.prepareVector(
                            candidate.jobId(),
                            plan.transactionId(),
                            selectedExecutorId,
                            plan.programFingerprint());
                } catch (RuntimeException | LinkageError preparationFailure) {
                    // 外部start前なので、prepare自体を確定できなかった候補は次tickへ延期する。
                    AE2CraftingOptimizer.LOGGER.error(
                            "Failed to prepare AQE Exact Vector parent job {}",
                            candidate.jobId(),
                            preparationFailure);
                    continue;
                }
                cluster.markDirty();
                try {
                    VectorStartResult started = Objects.requireNonNull(
                            selected.start(plan),
                            "Exact Vector executor start result");
                    if (!resolveVectorStartOutcome(
                            candidate,
                            plan,
                            selected,
                            started,
                            null)) {
                        continue;
                    }
                    activeTransactions++;
                    if (ACOConfig.logExactVectorDiagnostics()) {
                        AE2CraftingOptimizer.LOGGER.info(
                                "Started AQE Exact Vector parent job {}: {} logical execution(s), {} stage(s), {} tick(s), executor {}",
                                candidate.jobId(),
                                plan.logicalExecutions(),
                                plan.logicalStageCount(),
                                plan.durationTicks(),
                                selectedExecutorId);
                    }
                } catch (RuntimeException | LinkageError failure) {
                    if (resolveVectorStartOutcome(
                            candidate,
                            plan,
                            selected,
                            null,
                            failure)) {
                        activeTransactions++;
                    }
                }
            }
        }

        /**
         * start後は応答より永続Receiptを優先し、Receiptなしを証明できた時だけLeaseを戻す。
         *
         * @return 外部設備が未完了Receiptを所有している場合true
         */
        private boolean resolveVectorStartOutcome(
                BigCraftingRuntime.VectorCandidate<AEKey> candidate,
                PreparedVectorBatch plan,
                ExactVectorExecutor executor,
                VectorStartResult started,
                Throwable startFailure) {
            if (started != null
                    && !started.transactionId().equals(
                            plan.transactionId())) {
                host.quarantineVector(
                        candidate.jobId(),
                        plan.transactionId());
                cluster.markDirty();
                AE2CraftingOptimizer.LOGGER.error(
                        "Exact Vector executor returned another transaction ID for parent job {}",
                        candidate.jobId(),
                        startFailure);
                return false;
            }
            if (started != null
                    && started.started()
                    && !started.status().terminal()) {
                return true;
            }

            Optional<VectorTransactionSnapshot> snapshot;
            try {
                snapshot = executor.snapshot(
                        plan.transactionId());
            } catch (RuntimeException | LinkageError snapshotFailure) {
                if (startFailure != null) {
                    snapshotFailure.addSuppressed(startFailure);
                }
                host.quarantineVector(
                        candidate.jobId(),
                        plan.transactionId());
                cluster.markDirty();
                AE2CraftingOptimizer.LOGGER.error(
                        "AQE Exact Vector parent start outcome could not be proven for job {}",
                        candidate.jobId(),
                        snapshotFailure);
                return false;
            }
            if (snapshot.isEmpty()) {
                if (started != null && started.started()) {
                    // started=trueなのにReceiptが無い応答は、所有権を推測せず親Jobを隔離する。
                    host.quarantineVector(
                            candidate.jobId(),
                            plan.transactionId());
                    cluster.markDirty();
                    AE2CraftingOptimizer.LOGGER.error(
                            "AQE Exact Vector executor reported a started parent job {} without a receipt",
                            candidate.jobId());
                    return false;
                }
                /*
                 * 拒否または例外後に同じExecutorがReceiptなしを明示した場合だけ、
                 * 入力所有権は移っていないため親LeaseをPLANNEDへ戻せる。
                 */
                host.rollbackVector(
                        candidate.jobId(),
                        plan.transactionId());
                cluster.markDirty();
                if (startFailure != null) {
                    AE2CraftingOptimizer.LOGGER.warn(
                            "Rolled back AQE Exact Vector parent job {} after a receipt-free start failure",
                            candidate.jobId(),
                            startFailure);
                }
                return false;
            }

            VectorTransactionStatus remote =
                    snapshot.orElseThrow().status();
            if (remote == VectorTransactionStatus.CANCELLED) {
                host.rollbackVector(
                        candidate.jobId(),
                        plan.transactionId());
                cluster.markDirty();
                return false;
            }
            if (remote == VectorTransactionStatus.QUARANTINED
                    || remote == VectorTransactionStatus.COMPLETED) {
                host.quarantineVector(
                        candidate.jobId(),
                        plan.transactionId());
                cluster.markDirty();
                AE2CraftingOptimizer.LOGGER.error(
                        "AQE Exact Vector parent job {} returned unsafe start receipt {}",
                        candidate.jobId(),
                        remote,
                        startFailure);
                return false;
            }
            if (startFailure != null) {
                AE2CraftingOptimizer.LOGGER.warn(
                        "Recovered AQE Exact Vector parent job {} from its executor receipt",
                        candidate.jobId(),
                        startFailure);
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
                        ACOConfig
                                .getExactVectorTicksPerLogicalStage(),
                        ACOConfig
                                .getExactVectorEnergyMicroAePerLogicalExecution(),
                        BigInteger.ZERO,
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
            // 同一JVM内では世代番号が正本なので、Patternまたはrecipe変更を即時失効させる。
            if (PlanningRuntimeEpoch.current().equals(lease.planningEpoch())) {
                return lease.patternGeneration() < 0L
                        || lease.recipeGeneration() < 0L
                        || lease.patternGeneration() != currentPatternGeneration
                        || lease.recipeGeneration() != currentRecipeGeneration;
            }
            // 旧Schemaには再起動後の同一性証明がないため、推測でJobを継続しない。
            if (lease.planningEpoch().isEmpty() || lease.programFingerprint().isEmpty()) {
                return true;
            }
            try {
                var snapshot = Ae2CompiledCraftingGraphCache.getOrCompile(
                        cluster.getGrid(), cluster.getLevel());
                var currentProgram = snapshot.rootProgram(lease.requestedKey()).orElse(null);
                /*
                 * 再起動で世代番号だけが変わった場合は、正規化Fingerprintが完全一致する
                 * 同じ決定的Programだけを継続する。FingerprintはProgram単位でキャッシュされる。
                 */
                return currentProgram == null
                        || !lease.programFingerprint().equals(
                                Ae2BigCraftingPlanFactory.programFingerprint(currentProgram));
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
                IGrid grid = cluster.getGrid();
                ExactVectorExecutor executor = grid == null
                        ? null
                        : ExactVectorExecutorRegistry.findRegistered(grid)
                                .stream()
                                .filter(candidate ->
                                        candidate.identity().id().equals(
                                                vector.prepared().executorId()))
                                .findFirst()
                                .orElse(null);
                /*
                 * 未読込設備の入力所有権は推測できない。親Jobを消さず、設備が再登録されるまで
                 * キャンセル要求を拒否する。
                 */
                if (executor == null) {
                    AE2CraftingOptimizer.LOGGER.warn(
                            "Deferred cancellation of AQE Exact Vector job {} because executor {} is not loaded",
                            jobId,
                            vector.prepared().executorId());
                    return false;
                }
                boolean executorCancelled;
                Optional<VectorTransactionSnapshot> snapshot;
                try {
                    executorCancelled = executor.cancel(
                            vector.prepared().transactionId());
                    snapshot = executor.snapshot(
                            vector.prepared().transactionId());
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
                // 同一設備にReceiptが無い場合だけ、入力未所有のstart前Leaseとして戻す。
                if (snapshot.isEmpty()) {
                    host.rollbackVector(
                            jobId,
                            vector.prepared().transactionId());
                } else {
                    VectorTransactionStatus status =
                            snapshot.get().status();
                    if (status == VectorTransactionStatus.CANCELLED) {
                        host.rollbackVector(
                                jobId,
                                vector.prepared().transactionId());
                    } else if (status
                            == VectorTransactionStatus.QUARANTINED) {
                        host.quarantineVector(
                                jobId,
                                vector.prepared().transactionId());
                        cluster.recalculateRemainingStorage();
                        cluster.markDirty();
                        return true;
                    } else if (!executorCancelled) {
                        /*
                         * 出力移転後など、設備が安全に取消できない状態は親進捗を推測せず
                         * 隔離する。
                         */
                        host.quarantineVector(
                                jobId,
                                vector.prepared().transactionId());
                        cluster.recalculateRemainingStorage();
                        cluster.markDirty();
                        return true;
                    } else {
                        host.quarantineVector(
                                jobId,
                                vector.prepared().transactionId());
                        cluster.recalculateRemainingStorage();
                        cluster.markDirty();
                        return true;
                    }
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
            if (rollbackPending) {
                cluster.recalculateRemainingStorage();
                cluster.markDirty();
            }
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

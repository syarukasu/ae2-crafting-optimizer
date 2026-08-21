package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.access.CraftingJobTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingLogicTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.ExactCraftingJobAccess;
import com.syaru.ae2craftingoptimizer.access.ExactCraftingLogicAccess;
import com.syaru.ae2craftingoptimizer.api.batch.ExactPatternFormula;
import com.syaru.ae2craftingoptimizer.api.batch.v2.ProviderOwnedPatternBatchTarget;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchTarget;
import com.syaru.ae2craftingoptimizer.api.vector.ExactVectorDiagnostics;
import com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatch;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.Ae2BigCraftingPlanFactory;
import com.syaru.ae2craftingoptimizer.engine.Ae2CompiledCraftingGraphCache;
import com.syaru.ae2craftingoptimizer.engine.CompiledRootProgram;
import com.syaru.ae2craftingoptimizer.engine.ExactCraftingJobLedger;
import com.syaru.ae2craftingoptimizer.engine.ExactCraftingJobState;
import com.syaru.ae2craftingoptimizer.engine.PlanningRuntimeEpoch;
import com.syaru.ae2craftingoptimizer.engine.RecipeGenerationTracker;
import com.syaru.ae2craftingoptimizer.engine.craftingtable.PhysicalCraftingTreeTransaction;
import com.syaru.ae2craftingoptimizer.engine.vector.VectorBatchPlanValidator;
import com.syaru.ae2craftingoptimizer.engine.vector.VectorBatchPlanner;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import com.syaru.ae2craftingoptimizer.scheduler.PatternProviderRoutingCache;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Issue #115: 標準AE2 CraftingCPUCluster上のexact Jobを物理Receipt経路で進める。
 *
 * <p>CPU構造、容量判定、Job生成、CraftingLinkはAE2またはCPUアドオンが所有する。
 * このManagerはACO Sidecarを持つJobだけを列挙し、InsaneAEなどの固有クラスを参照しない。</p>
 */
public final class Ae2BigCraftingExecutionManager {
    private static final Map<CraftingCPUCluster, Controller> CONTROLLERS =
            new IdentityHashMap<>();
    private static int roundRobinCursor;

    private Ae2BigCraftingExecutionManager() {
    }

    /** exact Sidecarを設置または復元した標準AE2クラスタだけを追跡する。 */
    public static synchronized void register(CraftingCPUCluster cluster) {
        if (cluster == null) {
            return;
        }
        CONTROLLERS.computeIfAbsent(cluster, Controller::new);
    }

    /** 入力所有権を持たないJobを閉じた時だけ、追跡を即時解除する。 */
    public static synchronized void unregister(CraftingCPUCluster cluster) {
        Controller removed = CONTROLLERS.remove(cluster);
        if (removed != null) {
            removed.close();
        }
    }

    /** Server tick末尾で、同一Gridの共有予算を守りながら各exact Jobを一巡する。 */
    public static synchronized void tick(MinecraftServer server) {
        if (CONTROLLERS.isEmpty()) {
            return;
        }
        List<Controller> ordered = new ArrayList<>(CONTROLLERS.values());
        ordered.removeIf(controller -> !controller.belongsTo(server));
        ordered.sort(Comparator.comparing(Controller::stableKey));
        if (ordered.isEmpty()) {
            CONTROLLERS.entrySet().removeIf(entry -> !entry.getValue().belongsTo(server));
            roundRobinCursor = 0;
            return;
        }
        int start = Math.floorMod(roundRobinCursor, ordered.size());
        // 保存Cursorから全Controllerを一巡し、一つの巨大Jobが常に先頭で予算を取らないようにする。
        for (int offset = 0; offset < ordered.size(); offset++) {
            Controller controller = ordered.get(Math.floorMod(start + offset, ordered.size()));
            boolean alive;
            try {
                alive = controller.tick();
            } catch (RuntimeException | LinkageError failure) {
                controller.quarantine("standard AE2 exact executor failed", failure);
                alive = true;
            }
            if (!alive) {
                CONTROLLERS.remove(controller.cluster);
                controller.close();
            }
        }
        CONTROLLERS.entrySet().removeIf(entry -> !entry.getValue().belongsTo(server));
        roundRobinCursor = Math.floorMod(start + 1, Math.max(1, ordered.size()));
    }

    /** Server停止時にRuntime cacheだけを破棄する。永続正本はJob NBTに残る。 */
    public static synchronized void clear() {
        // 全ControllerのRuntime transaction参照を破棄し、次回はJob NBTから復元する。
        for (Controller controller : CONTROLLERS.values()) {
            controller.close();
        }
        CONTROLLERS.clear();
        roundRobinCursor = 0;
    }

    private static final class Controller {
        private final CraftingCPUCluster cluster;
        private final ProgramFingerprintRevalidationCache revalidatedPrograms =
                new ProgramFingerprintRevalidationCache();
        private PhysicalCraftingTreeTransaction transaction;
        private UUID transactionJobId;
        private ExactCraftingJobAccess lastExactJob;
        /** 直前にログへ出した待機理由のジョブ。理由が変わるまで同じ行を繰り返さない。 */
        private UUID lastReportedWaitingJobId;
        /** 直前にログへ出した待機理由。状態変化の境界だけを診断ログへ出す。 */
        private String lastReportedWaitingReason;
        /** 同じ待機理由を再通知するまでのサーバーtick数。1 tick = 50 ms。 */
        private int waitingTicksSinceLog;

        private Controller(CraftingCPUCluster cluster) {
            this.cluster = cluster;
        }

        private boolean belongsTo(MinecraftServer server) {
            return !cluster.isDestroyed()
                    && cluster.getLevel() != null
                    && cluster.getLevel().getServer() == server;
        }

        private String stableKey() {
            return cluster.getLevel().dimension().location()
                    + ":"
                    + cluster.getBoundsMin().asLong();
        }

        /** @return 同じクラスタを次tickも追跡する場合true。 */
        private boolean tick() {
            Context context = context();
            if (context == null) {
                transaction = null;
                transactionJobId = null;
                lastExactJob = null;
                return false;
            }
            lastExactJob = context.exactJob();
            ExactCraftingJobState state = context.state();
            if (state.quarantined()) {
                return true;
            }
            IGrid grid = cluster.getGrid();
            if (grid == null || !cluster.isActive()) {
                return true;
            }
            var graphSnapshot = Ae2CompiledCraftingGraphCache.getOrCompile(
                    grid,
                    cluster.getLevel());
            try {
                restoreOrStart(context, grid, graphSnapshot);
            } catch (PhysicalCraftingTreeTransaction.PatternUnavailableException deferred) {
                return true;
            } catch (RuntimeException | LinkageError failure) {
                if (!state.hasPhysicalExecution()) {
                    AE2CraftingOptimizer.LOGGER.error(
                            "Cancelled standard AE2 exact job {} before physical ownership because its plan could not be rebuilt",
                            context.jobId(),
                            failure);
                    finish(context, false);
                    return false;
                }
                quarantine("failed to restore standard AE2 exact physical execution", failure);
                return true;
            }
            // WorkerやConfig待ちではまだ物理所有権がないため、同じJobをそのまま維持する。
            if (transaction == null) {
                return true;
            }
            if (state.cancellationRequested()) {
                transaction.requestCancellation();
            }
            int operationBudget = ExactVectorGridTickBudget.claimActiveStages(
                    grid,
                    Math.max(1, transaction.plan().craftingSteps().size()));
            if (operationBudget == 0) {
                reportWaitingReason(
                        context,
                        "waiting for the exact physical execution tick budget",
                        operationBudget);
                return true;
            }
            long startedNanos = System.nanoTime();
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
            ExactVectorDiagnostics.activeTick(System.nanoTime() - startedNanos);
            reportWaitingOutcome(
                    context,
                    outcome,
                    operationBudget);
            /*
             * Forge 1.20.1のTransactionにはrevision診断がないため、実行tickごとに
             * Receipt会計と復旧NBTを同期する。Issue #115のexact Jobだけがこの経路へ入る。
             */
            reconcile(context, graphSnapshot);
            state.updatePhysicalExecution(transaction.save());
            cluster.markDirty();
            if (outcome.kind() == PhysicalCraftingTreeTransaction.Kind.COMPLETE) {
                if (!context.exactJob().aco$isExactAccountingBalanced()) {
                    quarantine("standard AE2 exact job completed with unbalanced counters", null);
                    return true;
                }
                ExactVectorDiagnostics.transactionCompleted();
                finish(context, true);
                return false;
            }
            if (outcome.kind() == PhysicalCraftingTreeTransaction.Kind.CANCELLED) {
                ExactVectorDiagnostics.transactionCancelled();
                finish(context, false);
                return false;
            }
            if (outcome.kind() == PhysicalCraftingTreeTransaction.Kind.QUARANTINED) {
                quarantine(outcome.detail(), null);
            }
            return true;
        }

        /**
         * 標準AE2 CPUの物理Transactionが待機した理由を、理由の変化ごとに一度だけ記録する。
         * 同一理由を毎tick出さないため、サーバーログを待機状態の証拠として使える。
         */
        private void reportWaitingOutcome(
                Context context,
                PhysicalCraftingTreeTransaction.TickOutcome outcome,
                int operationBudget) {
            if (outcome.kind() != PhysicalCraftingTreeTransaction.Kind.WAITING) {
                clearWaitingReport();
                return;
            }
            reportWaitingReason(context, outcome.detail(), operationBudget);
        }

        /**
         * Issue #125: 標準AE2 CPUが進めない区間の理由を、状態変化ごとに診断ログへ出す。
         * 同じ理由が続く場合は600 tickごとに一度だけ再通知し、ログスパムを避ける。
         */
        private void reportWaitingReason(
                Context context,
                String rawReason,
                int operationBudget) {
            if (!ACOConfig.logExactExecutionStalls()) {
                return;
            }
            UUID jobId = context.jobId();
            String reason = rawReason == null || rawReason.isBlank()
                    ? "waiting reason was not provided"
                    : rawReason;
            boolean changedReason = !jobId.equals(lastReportedWaitingJobId)
                    || !reason.equals(lastReportedWaitingReason);
            waitingTicksSinceLog++;
            // 同じジョブ・同じ理由の連続tickは抑制し、状態変化または600 tickごとに記録する。
            if (!changedReason && waitingTicksSinceLog < 600) {
                return;
            }
            lastReportedWaitingJobId = jobId;
            lastReportedWaitingReason = reason;
            waitingTicksSinceLog = 0;
            AE2CraftingOptimizer.LOGGER.info(
                    "ACO standard AE2 exact job waiting: jobId={}, transactionId={}, state={}, reason={}, operationBudget={}, consumedOperations={}",
                    jobId,
                    transaction.transactionId(),
                    transaction.state(),
                    reason,
                    operationBudget,
                    transaction.lastConsumedOperations());
        }

        private void clearWaitingReport() {
            lastReportedWaitingJobId = null;
            lastReportedWaitingReason = null;
            waitingTicksSinceLog = 0;
        }

        private Context context() {
            Object job = ((CraftingLogicTransactionAccess) (Object) cluster.craftingLogic)
                    .aco$getExecutingJob();
            if (!(job instanceof ExactCraftingJobAccess exactJob)
                    || !exactJob.aco$isExactJob()
                    || !(job instanceof CraftingJobTransactionAccess transactionAccess)) {
                return null;
            }
            ExactCraftingJobState state = exactJob.aco$getExactState();
            if (state == null) {
                throw new IllegalStateException("standard AE2 exact job lost its sidecar state");
            }
            return new Context(
                    transactionAccess.aco$getCraftingJobId(),
                    exactJob,
                    state);
        }

        private void restoreOrStart(
                Context context,
                IGrid grid,
                Ae2CompiledCraftingGraphCache.Snapshot graphSnapshot) {
            if (transaction != null && !context.jobId().equals(transactionJobId)) {
                throw new IllegalStateException("physical transaction belongs to another AE2 job");
            }
            if (transaction == null && context.state().hasPhysicalExecution()) {
                transaction = PhysicalCraftingTreeTransaction.load(
                        context.state().physicalExecution());
                if (!transaction.plan().parentJobId().equals(context.jobId())) {
                    throw new IllegalArgumentException(
                            "exact physical execution belongs to another AE2 job");
                }
                transactionJobId = context.jobId();
            }
            if (transaction != null) {
                return;
            }
            /* Issue #115: 新規開始が無効でも、開始済みTransactionの復元・取消は上で継続する。 */
            if (!ACOConfig.enableExactBigIntegerPhysicalExecution()) {
                return;
            }
            // 入力所有権取得前の世代不一致だけは、同じAE2 Jobを通常取消経路で閉じられる。
            if (isStale(context.state(), graphSnapshot)) {
                finish(context, false);
                return;
            }
            PreparedVectorBatch plan = prepare(context.jobId(), context.state(), graphSnapshot);
            if (!supportsPhysicalPlan(grid, graphSnapshot, plan)) {
                return;
            }
            ExactVectorGridTickBudget startBudget = ExactVectorGridTickBudget.forGrid(grid);
            if (!startBudget.tryStart()) {
                ExactVectorDiagnostics.startBudgetDeferred();
                return;
            }
            PhysicalCraftingTreeTransaction created = PhysicalCraftingTreeTransaction.create(
                    plan,
                    PhysicalCraftingTreeTransaction.capturePatternAccounting(
                            plan,
                            graphSnapshot,
                            cluster.getLevel()));
            validate(context, created.accountingSnapshot(graphSnapshot, cluster.getLevel()));
            context.state().beginPhysicalExecution(created.save());
            transaction = created;
            transactionJobId = context.jobId();
            cluster.markDirty();
            ExactVectorDiagnostics.planPrepared();
            ExactVectorDiagnostics.transactionStarted(
                    com.syaru.ae2craftingoptimizer.api.vector.VectorResourceMode.NETWORK_STORAGE);
        }

        private PreparedVectorBatch prepare(
                UUID jobId,
                ExactCraftingJobState state,
                Ae2CompiledCraftingGraphCache.Snapshot graphSnapshot) {
            long patternGeneration = ProviderPatternGenerationTracker.generation();
            long recipeGeneration = RecipeGenerationTracker.generation();
            CompiledRootProgram<AEKey> program = graphSnapshot.rootProgram(state.requestedKey())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "standard AE2 exact CPU root program is unavailable"));
            if (!state.programFingerprint().equals(
                    Ae2BigCraftingPlanFactory.programFingerprint(program))) {
                throw new IllegalArgumentException("standard AE2 exact CPU fingerprint changed");
            }
            int maximumBits = ACOConfig.getBigIntegerMaximumBits();
            CompiledRootProgram.BigInventorySnapshot<AEKey> inventory =
                    program.captureBigInventory(
                            key -> state.plannedInventory().getOrDefault(key, BigInteger.ZERO),
                            maximumBits);
            PreparedVectorBatch plan = VectorBatchPlanner.prepare(
                    UUID.randomUUID(),
                    jobId,
                    program,
                    inventory,
                    state.requestedAmount(),
                    state.programFingerprint(),
                    patternGeneration,
                    recipeGeneration,
                    maximumBits);
            VectorBatchPlanValidator.validate(
                    plan,
                    maximumBits,
                    ACOConfig.getExactVectorMaximumPatternNodes(),
                    ACOConfig.getExactVectorMaximumInputKeys(),
                    ACOConfig.getExactVectorMaximumOutputKeys());
            if (ProviderPatternGenerationTracker.generation() != patternGeneration
                    || RecipeGenerationTracker.generation() != recipeGeneration) {
                throw new IllegalStateException(
                        "standard AE2 exact graph changed while preparing its physical plan");
            }
            return plan;
        }

        private boolean supportsPhysicalPlan(
                IGrid grid,
                Ae2CompiledCraftingGraphCache.Snapshot snapshot,
                PreparedVectorBatch plan) {
            if (!(grid.getCraftingService() instanceof CraftingService service)) {
                return false;
            }
            // 全固有Patternについて、決定的作業台式と永続物理Targetを開始前に証明する。
            for (var step : plan.craftingSteps()) {
                IPatternDetails pattern = snapshot.pattern(step.patternId());
                if (pattern == null
                        || ExactPatternFormula.tryCreate(
                                        pattern,
                                        cluster.getLevel(),
                                        step.selectedInputs())
                                .isEmpty()) {
                    return false;
                }
                boolean found = false;
                // このPatternを所有するProvider候補だけを一巡する。
                for (ICraftingProvider provider : PatternProviderRoutingCache.candidates(
                        service,
                        pattern)) {
                    if (!(provider instanceof ProviderOwnedPatternBatchTarget owned)) {
                        continue;
                    }
                    BlockEntity target = owned.aco$getProviderOwnedBatchTarget();
                    if (target instanceof CraftingTableBatchTarget) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }

        private void validate(
                Context context,
                PhysicalCraftingTreeTransaction.AccountingSnapshot accounting) {
            if (!accounting.plannedPatternDefinitions().equals(context.state().taskTotals())
                    || !context.state().initialWaiting().isEmpty()) {
                throw new IllegalArgumentException(
                        "physical plan does not match standard AE2 exact-job accounting");
            }
        }

        private void reconcile(
                Context context,
                Ae2CompiledCraftingGraphCache.Snapshot graphSnapshot) {
            PhysicalCraftingTreeTransaction.AccountingSnapshot accounting =
                    transaction.accountingSnapshot(graphSnapshot, cluster.getLevel());
            validate(context, accounting);
            BigInteger remainingOutput = accounting.finalOutputReturned()
                    ? BigInteger.ZERO
                    : context.state().requestedAmount();
            context.exactJob().aco$reconcileExactAccounting(
                    accounting.dispatchedPatternDefinitions(),
                    accounting.introducedOutputs(),
                    accounting.creditedOutputs(),
                    remainingOutput);
            BigInteger exactRemaining = context.exactJob().aco$getExactRemainingOutput();
            cluster.updateOutput(exactRemaining.signum() == 0
                    ? null
                    : new GenericStack(
                            context.state().requestedKey(),
                            ExactCraftingJobLedger.saturatedLong(exactRemaining)));
        }

        private boolean isStale(
                ExactCraftingJobState state,
                Ae2CompiledCraftingGraphCache.Snapshot graphSnapshot) {
            long patternGeneration = ProviderPatternGenerationTracker.generation();
            long recipeGeneration = RecipeGenerationTracker.generation();
            if (PlanningRuntimeEpoch.current().equals(state.planningEpoch())
                    && state.patternGeneration() == patternGeneration
                    && state.recipeGeneration() == recipeGeneration) {
                return false;
            }
            if (revalidatedPrograms.contains(
                    patternGeneration,
                    recipeGeneration,
                    state.programFingerprint())) {
                return false;
            }
            CompiledRootProgram<AEKey> current = graphSnapshot.rootProgram(state.requestedKey())
                    .orElse(null);
            boolean matches = current != null
                    && state.programFingerprint().equals(
                            Ae2BigCraftingPlanFactory.programFingerprint(current));
            if (matches) {
                revalidatedPrograms.record(
                        patternGeneration,
                        recipeGeneration,
                        state.programFingerprint());
                ExactVectorDiagnostics.fingerprintRevalidated();
            }
            return !matches;
        }

        private void finish(Context context, boolean successful) {
            ((ExactCraftingLogicAccess) (Object) cluster.craftingLogic)
                    .aco$finishExactJob(successful);
            cluster.updateOutput(null);
            transaction = null;
            transactionJobId = null;
            lastExactJob = null;
            cluster.markDirty();
        }

        private void quarantine(String detail, Throwable failure) {
            String checked = detail == null || detail.isBlank()
                    ? "unknown standard AE2 exact-job failure"
                    : detail;
            if (transaction != null) {
                transaction.quarantineForAccounting(checked);
            }
            if (lastExactJob != null && lastExactJob.aco$getExactState() != null) {
                ExactCraftingJobState state = lastExactJob.aco$getExactState();
                if (transaction != null && state.hasPhysicalExecution()) {
                    state.updatePhysicalExecution(transaction.save());
                }
                state.quarantine();
                cluster.markDirty();
            }
            ExactVectorDiagnostics.transactionQuarantined();
            if (failure == null) {
                AE2CraftingOptimizer.LOGGER.error(
                        "Quarantined standard AE2 exact job: {}",
                        checked);
            } else {
                AE2CraftingOptimizer.LOGGER.error(
                        "Quarantined standard AE2 exact job: {}",
                        checked,
                        failure);
            }
        }

        private void close() {
            transaction = null;
            transactionJobId = null;
            lastExactJob = null;
        }
    }

    private record Context(
            UUID jobId,
            ExactCraftingJobAccess exactJob,
            ExactCraftingJobState state) {
    }
}

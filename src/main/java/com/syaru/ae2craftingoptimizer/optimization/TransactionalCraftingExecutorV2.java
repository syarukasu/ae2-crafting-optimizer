package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ICraftingInventory;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchBudget;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchCpuAccountingMode;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchEnergyAccountingMode;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReceipt;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReceiptStore;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchCommit;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PatternBatchV2Api;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PreparedPatternBatch;
import com.syaru.ae2craftingoptimizer.api.batch.v2.ProviderOwnedPatternBatchTarget;
import com.syaru.ae2craftingoptimizer.api.batch.v2.SourceRecoveryResult;
import com.syaru.ae2craftingoptimizer.api.batch.v2.TransactionalPatternBatchAdapter;
import com.syaru.ae2craftingoptimizer.access.CraftingJobTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingLogicTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingOwnerTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingTaskProgressAccess;
import com.syaru.ae2craftingoptimizer.batch.Ae2BatchSourceReconciler;
import com.syaru.ae2craftingoptimizer.batch.NativePatternBatchSupport;
import com.syaru.ae2craftingoptimizer.batch.PatternTaskFingerprint;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.transaction.BatchTransactionCoordinator;
import com.syaru.ae2craftingoptimizer.transaction.BatchTransactionRecord;
import com.syaru.ae2craftingoptimizer.scheduler.PatternProviderRoutingCache;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * V2 Native Batchを実行する中心処理。
 * 一つの取引をprepare、機械側受理、AE2側会計、commitの順で進め、送信元と送信先の
 * Receiptが一致した完全実行数だけを成功として返す。証明不能時はNOT_HANDLEDで元処理へ戻す。
 */
public final class TransactionalCraftingExecutorV2 {
    public static final int NOT_HANDLED = -1;
    private static final Set<String> FAILURES_LOGGED =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private TransactionalCraftingExecutorV2() {
    }

    public static int tryExecute(
            Object logic,
            int maxPatterns,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level) {
        if (!ACOConfig.enableTransactionalBatchingV2()
                || logic == null
                || maxPatterns < 1
                || craftingService == null
                || energyService == null
                || level == null) {
            return NOT_HANDLED;
        }
        // Instantは機械の処理時間を消す機能ではない。同じCPU呼び出し内で複数の安全な取引を
        // 時間・操作数・取引数の予算内だけ連続して配送する。
        boolean instantDispatch = ACOConfig.enableInstantPatternDispatch();
        int maximumTransactions = instantDispatch
                ? ACOConfig.getMaxInstantPatternDispatchTransactions()
                : 1;
        long deadline = instantDispatch
                ? deadlineAfterMillis(ACOConfig.getInstantPatternDispatchTimeBudgetMillis())
                : Long.MAX_VALUE;
        int acceptedTotal = 0;
        int attempted = 0;
        int committed = 0;
        while (acceptedTotal < maxPatterns
                && attempted < maximumTransactions
                && hasTimeRemaining(deadline)) {
            int result = tryExecuteSingle(
                    logic,
                    maxPatterns - acceptedTotal,
                    craftingService,
                    energyService,
                    level);
            if (result == NOT_HANDLED) {
                recordInstantDispatch(instantDispatch, committed, acceptedTotal);
                return acceptedTotal == 0 ? NOT_HANDLED : acceptedTotal;
            }
            attempted++;
            if (result <= 0) {
                recordInstantDispatch(instantDispatch, committed, acceptedTotal);
                return acceptedTotal == 0 ? result : acceptedTotal;
            }
            committed++;
            acceptedTotal = Math.addExact(acceptedTotal, result);
            if (!instantDispatch) {
                break;
            }
        }
        recordInstantDispatch(instantDispatch, committed, acceptedTotal);
        return acceptedTotal > 0 ? acceptedTotal : 0;
    }

    private static void recordInstantDispatch(
            boolean instantDispatch,
            int transactions,
            int acceptedExecutions) {
        if (instantDispatch) {
            OptimizationMetrics.recordInstantPatternDispatch(transactions, acceptedExecutions);
        }
    }

    private static int tryExecuteSingle(
            Object logic,
            int maxPatterns,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level) {
        if (logic == null) {
            return NOT_HANDLED;
        }
        BatchSourceReceiptStore sourceReceipts = Ae2BatchSourceReconciler.receiptStore(logic);
        if (sourceReceipts != null
                && (!sourceReceipts.aco$isBatchSourceReceiptLedgerHealthy()
                        || sourceReceipts.aco$hasAnyUnresolvedBatchSourceReceipt())) {
            return 0;
        }
        if (!ACOConfig.enableTransactionalBatchingV2()
                || !(level instanceof ServerLevel serverLevel)
                || maxPatterns < 1
                || craftingService == null
                || energyService == null) {
            return NOT_HANDLED;
        }
        if (sourceReceipts == null) {
            return NOT_HANDLED;
        }

        try {
            if (!(logic instanceof CraftingLogicTransactionAccess logicAccess)) {
                throw new IllegalStateException(
                        "ACO transaction access mixin is missing from " + logic.getClass().getName());
            }
            Object job = logicAccess.aco$getExecutingJob();
            CraftingOwnerTransactionAccess owner = logicAccess.aco$getCraftingOwner();
            ICraftingInventory inventory = logicAccess.aco$getCraftingInventory();
            if (job == null || owner == null) {
                return NOT_HANDLED;
            }
            if (!(job instanceof CraftingJobTransactionAccess jobAccess)) {
                throw new IllegalStateException(
                        "ACO executing-job access mixin is missing from " + job.getClass().getName());
            }
            Map<IPatternDetails, Object> tasks = jobAccess.aco$getTasks();
            if (tasks.isEmpty()) {
                return NOT_HANDLED;
            }

            Iterator<Map.Entry<IPatternDetails, Object>> iterator = tasks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<IPatternDetails, Object> task = iterator.next();
                IPatternDetails details = task.getKey();
                if (details == null || !(task.getValue() instanceof CraftingTaskProgressAccess progress)) {
                    continue;
                }
                String taskFingerprint = PatternTaskFingerprint.of(details);
                if (sourceReceipts.aco$hasUnresolvedBatchSourceReceipt(taskFingerprint)) {
                    return 0;
                }
                long remaining = progress.aco$getTaskProgress();
                // 一回だけの作業台クラフトもAAC実Workerへ渡し、注文量で実行経路を変えない。
                if (remaining < 1L) {
                    continue;
                }
                ExactPlan plan = ExactPlan.create(details, inventory, level);
                if (plan == null) {
                    continue;
                }
                V2Selection selection = selectProvider(
                        craftingService,
                        details,
                        plan,
                        jobAccess.aco$getCraftingJobId(),
                        level);
                if (selection == null) {
                    continue;
                }

                BatchCpuAccountingMode accountingMode =
                        selection.adapter().cpuAccountingMode();
                /*
                 * 通常Adapterは従来どおり論理回数をCPU予算へ数える。
                 * 実ワーカーが一つの仕事として所有するAdapterだけ、論理long回数を
                 * maxPatternsから切り離し、CPUには物理配送一回として返す。
                 */
                long requested = BatchExecutionOffer.select(
                        remaining,
                        maxPatterns,
                        accountingMode,
                        ACOConfig.getNativeBatchMaximumExecutions());
                requested = plan.safeBatchSize(requested);
                requested = selection.adapter().limitExecutions(selection.context(), requested);
                requested = limitByWaitingFor(
                        plan,
                        requested,
                        jobAccess);
                BatchEnergyAccountingMode energyMode =
                        selection.adapter().energyAccountingMode();
                /*
                 * 実Workerが電力と進捗を所有する場合、AE2 CPU側で論理N回分を
                 * 二重課金しない。その他のAdapterは従来の送信元電力会計を維持する。
                 */
                if (energyMode
                        == BatchEnergyAccountingMode.SOURCE_LOGICAL_EXECUTIONS) {
                    requested = limitByEnergy(
                            plan.inputsPerExecution(),
                            requested,
                            energyService);
                }
                requested = limitByInventory(plan.inputsPerExecution(), requested, inventory);
                if (requested < 1L) {
                    continue;
                }

                PatternBatchBudget budget = new PatternBatchBudget(requested, Long.MAX_VALUE);
                UUID transactionId = UUID.randomUUID();
                long preparedLimit = requested;
                PreparedPatternBatch prepared = selection.adapter().prepare(
                        selection.context(), budget, transactionId);
                if (!transactionId.equals(prepared.transactionId())
                        || prepared.offeredExecutions() > preparedLimit) {
                    throw new IllegalStateException(
                            "V2 adapter returned a mismatched transaction id or exceeded its execution budget");
                }
                requested = prepared.offeredExecutions();
                if (requested < 1L) {
                    continue;
                }
                if (!matchesPreparedBatch(prepared, plan, requested)) {
                    throw new IllegalStateException(
                            "V2 adapter prepared inputs or outputs that do not match the exact AE2 pattern plan");
                }
                double power = energyMode
                                == BatchEnergyAccountingMode.TARGET_PHYSICAL_OPERATION
                        ? 0.0D
                        : CraftingCpuHelper.calculatePatternPower(
                                scaleCounters(
                                        plan.inputsPerExecution(),
                                        requested));
                if (!Double.isFinite(power) || power < 0.0D) {
                    continue;
                }
                if (!NativeBatchTargetGuard.tryClaim(
                        level, selection.context().target().getBlockPos())) {
                    continue;
                }
                var sourceData = Ae2BatchSourceReconciler.createSourceData(
                        logic, owner, details, power, remaining);
                String patternFingerprint = NativePatternBatchSupport.fingerprint(selection.context());
                BatchTransactionCoordinator.Handle transaction = BatchTransactionCoordinator.open(
                        serverLevel.getServer(),
                        selection.adapter().id(),
                        Ae2BatchSourceReconciler.ID,
                        prepared,
                        level.dimension().location(),
                        selection.context().target().getBlockPos(),
                        patternFingerprint,
                        sourceData,
                        level.getGameTime());
                if (transaction == null) {
                    return NOT_HANDLED;
                }

                BatchSourceReceipt staged = new BatchSourceReceipt(
                        transactionId,
                        BatchSourceReceipt.State.STAGED,
                        requested,
                        taskFingerprint,
                        0,
                        level.getGameTime());
                if (!sourceReceipts.aco$stageBatchSourceReceipt(staged)) {
                    transaction.rolledBack(level.getGameTime());
                    return NOT_HANDLED;
                }

                boolean targetAccepted = false;
                boolean commitStarted = false;
                try {
                    owner.aco$markCraftingOwnerDirty();
                    sourceReceipts.aco$advanceBatchSourceReceipt(
                            transactionId, BatchSourceReceipt.State.EXTRACTING, 0, level.getGameTime());
                    owner.aco$markCraftingOwnerDirty();
                    boolean extractionComplete = extractBatch(
                            plan,
                            inventory,
                            requested,
                            sourceReceipts,
                            transactionId,
                            owner,
                            level.getGameTime());
                    if (!extractionComplete) {
                        selection.adapter().rollback(selection.context(), prepared);
                        SourceRecoveryResult rollback = Ae2BatchSourceReconciler.INSTANCE.rollbackPrepared(
                                serverLevel,
                                transaction.record());
                        return finishRollback(
                                transaction,
                                rollback,
                                serverLevel,
                                selection.adapter(),
                                selection.context(),
                                level.getGameTime(),
                                NOT_HANDLED,
                                "source inventory changed before extraction completed");
                    }
                    sourceReceipts.aco$advanceBatchSourceReceipt(
                            transactionId, BatchSourceReceipt.State.EXTRACTED, 0, level.getGameTime());
                    owner.aco$markCraftingOwnerDirty();
                    commitStarted = true;
                    PatternBatchCommit commit = selection.adapter().commit(selection.context(), prepared);
                    long accepted = commit.acceptedExecutions();
                    if (accepted != 0L && accepted != requested) {
                        throw new IllegalStateException(
                                "V2 native adapters must accept all or zero executions, got " + accepted
                                        + " of " + requested);
                    }
                    if (accepted == 0L) {
                        commitStarted = false;
                        selection.adapter().rollback(selection.context(), prepared);
                        SourceRecoveryResult rollback = Ae2BatchSourceReconciler.INSTANCE.rollbackPrepared(
                                serverLevel, transaction.record());
                        return finishRollback(
                                transaction,
                                rollback,
                                serverLevel,
                                selection.adapter(),
                                selection.context(),
                                level.getGameTime(),
                                0,
                                "target rejected the prepared batch");
                    }

                    transaction.targetAccepted(commit, level.getGameTime());
                    targetAccepted = true;
                    commitStarted = false;
                    sourceReceipts.aco$advanceBatchSourceReceipt(
                            transactionId, BatchSourceReceipt.State.TARGET_ACCEPTED, 0, level.getGameTime());
                    owner.aco$markCraftingOwnerDirty();

                    SourceRecoveryResult accounting = Ae2BatchSourceReconciler.INSTANCE.accountAccepted(
                            serverLevel, transaction.record());
                    if (accounting == SourceRecoveryResult.RETRY) {
                        safeReconciliation(
                                transaction,
                                level.getGameTime(),
                                "source accounting is incomplete and will be retried");
                        return 0;
                    }
                    if (accounting == SourceRecoveryResult.QUARANTINE) {
                        safeQuarantine(
                                transaction,
                                level.getGameTime(),
                                "source accounting could not be proven safe");
                        return 0;
                    }
                    BatchTransactionRecord resolvedRecord = transaction.record();
                    transaction.accounted(level.getGameTime());
                    forgetResolvedLive(
                            serverLevel,
                            selection.adapter(),
                            selection.context(),
                            resolvedRecord);
                    OptimizationMetrics.recordNativePatternBatch(selection.adapter().id().toString(), accepted);
                    return accountingMode == BatchCpuAccountingMode.SINGLE_PHYSICAL_OPERATION
                            ? 1
                            : Math.toIntExact(accepted);
                } catch (Throwable failure) {
                    if (!targetAccepted && !commitStarted) {
                        try {
                            selection.adapter().rollback(selection.context(), prepared);
                            SourceRecoveryResult rollback = Ae2BatchSourceReconciler.INSTANCE.rollbackPrepared(
                                    serverLevel, transaction.record());
                            logFailure(logic.getClass(), failure);
                            return finishRollback(
                                    transaction,
                                    rollback,
                                    serverLevel,
                                    selection.adapter(),
                                    selection.context(),
                                    level.getGameTime(),
                                    NOT_HANDLED,
                                    "source rollback will be retried after " + failure);
                        } catch (Throwable rollbackFailure) {
                            safeQuarantine(
                                    transaction,
                                    level.getGameTime(),
                                    "source rollback outcome is uncertain: " + rollbackFailure);
                            logFailure(logic.getClass(), failure);
                            return 0;
                        }
                    }
                    safeReconciliation(
                            transaction,
                            level.getGameTime(),
                            targetAccepted
                                    ? "source accounting failed: " + failure
                                    : "target commit outcome is uncertain: " + failure);
                    logFailure(logic.getClass(), failure);
                    return 0;
                }
            }
            return NOT_HANDLED;
        } catch (Throwable failure) {
            logFailure(logic.getClass(), failure);
            return NOT_HANDLED;
        }
    }

    private static long deadlineAfterMillis(int milliseconds) {
        long now = System.nanoTime();
        long budget = Math.multiplyExact((long) milliseconds, 1_000_000L);
        return now > Long.MAX_VALUE - budget ? Long.MAX_VALUE : now + budget;
    }

    private static boolean hasTimeRemaining(long deadlineNanos) {
        return deadlineNanos == Long.MAX_VALUE || System.nanoTime() < deadlineNanos;
    }

    @Nullable
    private static V2Selection selectProvider(
            CraftingService service,
            IPatternDetails details,
            ExactPlan plan,
            UUID craftingJobId,
            Level level) {
        for (ICraftingProvider provider : PatternProviderRoutingCache.candidates(service, details)) {
            if (provider.isBusy()) {
                continue;
            }
            if (provider instanceof ProviderOwnedPatternBatchTarget ownedTarget) {
                PatternBatchContext ownedContext = PatternBatchContext.providerOwned(
                        provider,
                        details,
                        plan.inputsPerExecution(),
                        plan.outputsPerExecution(),
                        plan.remainingOutputsPerExecution(),
                        level,
                        ownedTarget.aco$getProviderOwnedBatchTarget(),
                        craftingJobId);
                TransactionalPatternBatchAdapter ownedAdapter =
                        PatternBatchV2Api.find(ownedContext).orElse(null);
                if (ownedAdapter != null) {
                    return new V2Selection(ownedAdapter, ownedContext);
                }
            }
            PatternProviderBatchEligibility.BatchTarget target =
                    PatternProviderBatchEligibility.inspectV2(provider, details, level);
            if (target == null) {
                continue;
            }
            PatternBatchContext context = new PatternBatchContext(
                    provider,
                    details,
                    plan.inputsPerExecution(),
                    plan.outputsPerExecution(),
                    level,
                    target.providerSide(),
                    target.targetSide(),
                    target.target(),
                    target.deterministicTarget());
            TransactionalPatternBatchAdapter adapter = PatternBatchV2Api.find(context).orElse(null);
            if (adapter != null) {
                return new V2Selection(adapter, context);
            }
        }
        return null;
    }

    private static long limitByEnergy(
            KeyCounter[] inputs,
            long requested,
            IEnergyService energyService) {
        double perExecution = CraftingCpuHelper.calculatePatternPower(inputs);
        if (!Double.isFinite(perExecution) || perExecution < 0.0D) {
            return 0L;
        }
        if (perExecution == 0.0D) {
            return requested;
        }
        double total = perExecution * requested;
        if (!Double.isFinite(total)) {
            total = Double.MAX_VALUE;
        }
        double available = energyService.extractAEPower(
                total, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        return Math.min(requested, Math.max(0L, (long) Math.floor((available + 0.01D) / perExecution)));
    }

    private static long limitByInventory(
            KeyCounter[] perExecution,
            long requested,
            ICraftingInventory inventory) {
        Map<AEKey, BigInteger> totals = new java.util.HashMap<>();
        /*
         * 同じ素材が複数slotにある時も一回分の合計へ畳み込む。
         * slotごとに同じ在庫残量を再利用すると、9枠レシピを最大9倍過大評価するため。
         */
        for (KeyCounter slot : perExecution) {
            for (var input : slot) {
                if (input.getLongValue() <= 0L) {
                    return 0L;
                }
                totals.merge(
                        input.getKey(),
                        BigInteger.valueOf(input.getLongValue()),
                        BigInteger::add);
            }
        }

        return ExactBatchInputLimiter.limit(
                requested,
                totals,
                (key, requestedAmount) -> inventory.extract(
                        key,
                        requestedAmount,
                        Actionable.SIMULATE));
    }

    private static long limitByWaitingFor(
            ExactPlan plan,
            long requested,
            CraftingJobTransactionAccess job) {
        Map<AEKey, BigInteger> perExecution =
                new java.util.HashMap<>();
        appendCounterTotals(
                perExecution,
                plan.outputsPerExecution());
        appendCounterTotals(
                perExecution,
                plan.remainingOutputsPerExecution());

        BigInteger safe = BigInteger.valueOf(requested);
        BigInteger maximum = BigInteger.valueOf(Long.MAX_VALUE);
        for (Map.Entry<AEKey, BigInteger> output :
                perExecution.entrySet()) {
            long current =
                    job.aco$getWaitingForAmount(output.getKey());
            // 負数は既に会計が壊れているため、新しいBatchを一切積まない。
            if (current < 0L
                    || output.getValue().signum() <= 0) {
                return 0L;
            }
            BigInteger headroom =
                    maximum.subtract(BigInteger.valueOf(current));
            safe = safe.min(
                    headroom.divide(output.getValue()));
        }
        return safe.longValueExact();
    }

    private static void appendCounterTotals(
            Map<AEKey, BigInteger> target,
            KeyCounter source) {
        for (var entry : source) {
            /*
             * 主出力と返却物が同じキーでも中間合計をlongへ落とさず、
             * 最後にwaitingForのsigned-long余白と比較する。
             */
            target.merge(
                    entry.getKey(),
                    BigInteger.valueOf(entry.getLongValue()),
                    BigInteger::add);
        }
    }

    private static boolean extractBatch(
            ExactPlan plan,
            ICraftingInventory inventory,
            long executions,
            BatchSourceReceiptStore sourceReceipts,
            UUID transactionId,
            CraftingOwnerTransactionAccess owner,
            long gameTick) {
        KeyCounter[] requested = scaleCounters(plan.inputsPerExecution(), executions);
        for (KeyCounter slot : requested) {
            for (var input : slot) {
                long amount = input.getLongValue();
                if (inventory.extract(input.getKey(), amount, Actionable.SIMULATE) != amount) {
                    return false;
                }
                long actual = inventory.extract(input.getKey(), amount, Actionable.MODULATE);
                if (actual > 0L) {
                    // 実際に抜けた直後、その実数を同じCPU所有者のReceiptへ記録する。
                    // 以後の失敗ではこの台帳だけを戻すため、予定全量による複製を防げる。
                    sourceReceipts.aco$recordExtractedBatchSourceInput(
                            transactionId,
                            new GenericStack(input.getKey(), actual),
                            gameTick);
                    owner.aco$markCraftingOwnerDirty();
                }
                if (actual != amount) {
                    throw new IllegalStateException("AE2 inventory changed between simulation and extraction");
                }
            }
        }
        return true;
    }

    private static KeyCounter[] scaleCounters(KeyCounter[] source, long executions) {
        KeyCounter[] result = new KeyCounter[source.length];
        for (int index = 0; index < source.length; index++) {
            result[index] = scaleCounter(source[index], executions);
        }
        return result;
    }

    private static KeyCounter scaleCounter(KeyCounter source, long executions) {
        KeyCounter result = new KeyCounter();
        for (var entry : source) {
            result.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), executions));
        }
        return result;
    }

    private static boolean matchesPreparedBatch(
            PreparedPatternBatch prepared,
            ExactPlan plan,
            long executions) {
        return normalizedStacks(prepared.aggregateInputs()).equals(normalizedStacks(
                        flattenCounters(scaleCounters(plan.inputsPerExecution(), executions))))
                && normalizedStacks(prepared.expectedOutputs()).equals(normalizedStacks(
                        plan.scaleExpectedOutputs(executions)));
    }

    private static List<GenericStack> flattenCounters(KeyCounter[] counters) {
        List<GenericStack> result = new ArrayList<>();
        for (KeyCounter counter : counters) {
            for (var entry : counter) {
                result.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }
        return List.copyOf(result);
    }

    private static List<ComparableStack> normalizedStacks(List<GenericStack> stacks) {
        List<ComparableStack> result = new ArrayList<>(stacks.size());
        for (GenericStack stack : stacks) {
            if (stack == null || stack.amount() <= 0L) {
                throw new IllegalArgumentException(
                        "prepared batch contains a null or non-positive stack");
            }
            result.add(new ComparableStack(
                    stack.what().toTagGeneric().toString(),
                    stack.amount()));
        }
        result.sort(Comparator.comparing(ComparableStack::key)
                .thenComparingLong(ComparableStack::amount));
        return List.copyOf(result);
    }

    private static void logFailure(Class<?> logicClass, Throwable failure) {
        String key = logicClass.getName() + ':' + failure.getClass().getName() + ':' + failure.getMessage();
        if (FAILURES_LOGGED.add(key)) {
            AE2CraftingOptimizer.LOGGER.warn(
                    "ACO V2 native batch fell back or entered recovery for {}: {}",
                    logicClass.getName(), failure.toString());
        }
    }

    private static void safeReconciliation(
            BatchTransactionCoordinator.Handle transaction,
            long gameTick,
            String detail) {
        try {
            transaction.reconciliationRequired(gameTick, detail);
        } catch (Throwable journalFailure) {
            AE2CraftingOptimizer.LOGGER.error(
                    "ACO could not persist V2 reconciliation state: {}",
                    journalFailure.toString());
        }
    }

    private static void safeQuarantine(
            BatchTransactionCoordinator.Handle transaction,
            long gameTick,
            String detail) {
        try {
            transaction.quarantine(gameTick, detail);
        } catch (Throwable journalFailure) {
            AE2CraftingOptimizer.LOGGER.error(
                    "ACO could not persist V2 quarantine state: {}",
                    journalFailure.toString());
        }
    }

    private static int finishRollback(
            BatchTransactionCoordinator.Handle transaction,
            SourceRecoveryResult result,
            ServerLevel level,
            TransactionalPatternBatchAdapter adapter,
            PatternBatchContext context,
            long gameTick,
            int completeResult,
            String detail) {
        return switch (result) {
            case COMPLETE -> {
                BatchTransactionRecord resolvedRecord = transaction.record();
                transaction.rolledBack(gameTick);
                forgetResolvedLive(level, adapter, context, resolvedRecord);
                yield completeResult;
            }
            case RETRY -> {
                safeReconciliation(transaction, gameTick, detail);
                yield 0;
            }
            case QUARANTINE -> {
                safeQuarantine(transaction, gameTick, detail);
                yield 0;
            }
        };
    }

    private static void forgetResolvedLive(
            ServerLevel level,
            TransactionalPatternBatchAdapter adapter,
            PatternBatchContext context,
            BatchTransactionRecord record) {
        try {
            Ae2BatchSourceReconciler.INSTANCE.forgetResolvedSource(level, record);
        } catch (Throwable cleanupFailure) {
            AE2CraftingOptimizer.LOGGER.warn(
                    "ACO retained a terminal source receipt after journal completion {}: {}",
                    record.id(), cleanupFailure.toString());
        }
        try {
            adapter.forgetResolvedTarget(context, record.id());
        } catch (Throwable cleanupFailure) {
            AE2CraftingOptimizer.LOGGER.warn(
                    "ACO retained a terminal target receipt after journal completion {}: {}",
                    record.id(), cleanupFailure.toString());
        }
    }

    private record V2Selection(
            TransactionalPatternBatchAdapter adapter,
            PatternBatchContext context) {
    }

    private record ComparableStack(String key, long amount) {
    }

    private record ExactPlan(
            KeyCounter[] inputsPerExecution,
            KeyCounter outputsPerExecution,
            KeyCounter remainingOutputsPerExecution) {
        @Nullable
        private static ExactPlan create(
                IPatternDetails details,
                ICraftingInventory inventory,
                Level level) {
            /*
             * 通常作業台PatternはIMolecularAssemblerSupportedPattern側で
             * supportsPushInputsToExternalInventory=falseを返す。加工Pattern判定を
             * ここへ流用せず、作業台Patternかどうかを型で判定する。
             */
            if (!(details instanceof appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern)) {
                return null;
            }
            KeyCounter[] inputs = new KeyCounter[details.getInputs().length];
            try {
                for (int index = 0; index < details.getInputs().length; index++) {
                    IPatternDetails.IInput input = details.getInputs()[index];
                    var possible = input.getPossibleInputs();
                    if (input.getMultiplier() <= 0L || possible.length == 0) {
                        return null;
                    }
                    GenericStack selected = selectConcreteInput(
                            input,
                            possible,
                            inventory,
                            level);
                    if (selected == null) {
                        return null;
                    }
                    long amount = Math.multiplyExact(
                            selected.amount(),
                            input.getMultiplier());
                    KeyCounter slot = inputs[index] = new KeyCounter();
                    slot.add(selected.what(), amount);
                }
                KeyCounter outputs = new KeyCounter();
                for (var output : details.getOutputs()) {
                    if (output.amount() <= 0L) {
                        return null;
                    }
                    outputs.set(
                            output.what(), Math.addExact(outputs.get(output.what()), output.amount()));
                }
                KeyCounter remainingOutputs = new KeyCounter();
                for (int index = 0; index < details.getInputs().length; index++) {
                    IPatternDetails.IInput input = details.getInputs()[index];
                    AEKey selectedKey = firstKey(inputs[index]);
                    AEKey remainingKey = input.getRemainingKey(selectedKey);
                    if (remainingKey != null) {
                        long amount = input.getMultiplier();
                        remainingOutputs.set(
                                remainingKey,
                                Math.addExact(
                                        remainingOutputs.get(remainingKey),
                                        amount));
                    }
                }
                return new ExactPlan(inputs, outputs, remainingOutputs);
            } catch (ArithmeticException ignored) {
                return null;
            }
        }

        @Nullable
        private static GenericStack selectConcreteInput(
                IPatternDetails.IInput input,
                GenericStack[] candidates,
                ICraftingInventory inventory,
                Level level) {
            GenericStack selected = null;
            long selectedCrafts = -1L;
            for (GenericStack candidate : candidates) {
                if (candidate == null
                        || candidate.amount() <= 0L
                        || !input.isValid(candidate.what(), level)) {
                    continue;
                }
                long perCraft;
                try {
                    perCraft = Math.multiplyExact(
                            candidate.amount(),
                            input.getMultiplier());
                } catch (ArithmeticException overflow) {
                    continue;
                }
                long available = inventory.extract(
                        candidate.what(),
                        Long.MAX_VALUE,
                        Actionable.SIMULATE);
                long crafts = available / perCraft;
                // 在庫が最も多い一種類だけを選び、同じBatch内で代替素材を混在させない。
                if (crafts > selectedCrafts) {
                    selected = candidate;
                    selectedCrafts = crafts;
                }
            }
            return selected;
        }

        private static AEKey firstKey(KeyCounter counter) {
            for (var entry : counter) {
                return entry.getKey();
            }
            throw new IllegalStateException(
                    "exact pattern input slot is unexpectedly empty");
        }

        private long safeBatchSize(long requested) {
            long safe = requested;
            // 各slotは個別に抽出・Receipt保存するため、同一キーが複数slotにあっても合算しない。
            for (KeyCounter slot : inputsPerExecution) {
                for (var input : slot) {
                    safe = Math.min(
                            safe,
                            Long.MAX_VALUE / input.getLongValue());
                }
            }
            for (var output : outputsPerExecution) {
                safe = Math.min(safe, Long.MAX_VALUE / output.getLongValue());
            }
            for (var output : remainingOutputsPerExecution) {
                safe = Math.min(safe, Long.MAX_VALUE / output.getLongValue());
            }
            return safe;
        }

        private List<GenericStack> scaleExpectedOutputs(long executions) {
            List<GenericStack> result = new ArrayList<>();
            appendScaled(result, outputsPerExecution, executions);
            appendScaled(result, remainingOutputsPerExecution, executions);
            return List.copyOf(result);
        }

        private static void appendScaled(
                List<GenericStack> target,
                KeyCounter source,
                long executions) {
            for (var entry : source) {
                target.add(new GenericStack(
                        entry.getKey(),
                        Math.multiplyExact(
                                entry.getLongValue(),
                                executions)));
            }
        }
    }
}

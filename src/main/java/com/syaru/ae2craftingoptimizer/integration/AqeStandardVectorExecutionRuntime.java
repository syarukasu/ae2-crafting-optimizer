package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.access.AqeStandardVectorHost;
import com.syaru.ae2craftingoptimizer.access.CraftingIslandJobAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingTaskProgressAccess;
import com.syaru.ae2craftingoptimizer.api.vector.ExactStack;
import com.syaru.ae2craftingoptimizer.api.vector.ExactVectorDiagnostics;
import com.syaru.ae2craftingoptimizer.api.vector.ExactVectorExecutor;
import com.syaru.ae2craftingoptimizer.api.vector.ExactVectorExecutorRegistry;
import com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatch;
import com.syaru.ae2craftingoptimizer.api.vector.VectorExecutionOffer;
import com.syaru.ae2craftingoptimizer.api.vector.VectorResourceMode;
import com.syaru.ae2craftingoptimizer.api.vector.VectorStartResult;
import com.syaru.ae2craftingoptimizer.api.vector.VectorTransactionSnapshot;
import com.syaru.ae2craftingoptimizer.api.vector.VectorTransactionStatus;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingIslandCompiler;
import com.syaru.ae2craftingoptimizer.engine.CompiledCraftingIsland;
import com.syaru.ae2craftingoptimizer.engine.RecipeGenerationTracker;
import com.syaru.ae2craftingoptimizer.engine.vector.VectorBatchPlanValidator;
import com.syaru.ae2craftingoptimizer.engine.vector.VectorPlanFingerprint;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

/**
 * AdvancedAE標準Jobを、一つの永続HOST_ESCROWED Vector Transactionとして実行する。
 */
public final class AqeStandardVectorExecutionRuntime {
    private static final String NBT_STATE = "acoStandardExactVector";
    /** 1ミリ秒をSystem.nanoTime用のナノ秒へ変換する固定倍率。 */
    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;

    private AqeStandardVectorExecutionState state;
    private boolean requiresPostLoadValidation;

    /**
     * @return 同tickのAdvancedAE通常Pattern Pushを停止する場合true
     */
    public boolean tick(AqeStandardVectorHost host) {
        Objects.requireNonNull(host, "host");
        Object currentJob = host.aco$getStandardVectorJob();
        if (state == null) {
            // 機能OFFまたはJobなしでは、AdvancedAE本来の実行へ完全に戻す。
            if (!ACOConfig.enableAqeStandardVectorJobs()
                    || currentJob == null) {
                return false;
            }
            return tryPrepare(host, currentJob);
        }
        if (state.phase()
                == AqeStandardVectorExecutionState.Phase.QUARANTINED) {
            // 隔離済みReceiptは再実行も再ログ出力もせず、管理者が確認できる状態で固定する。
            return true;
        }

        // Jobが消えたのにReceiptだけ残る状態は、成果物会計の成否を推測せず隔離する。
        if (currentJob == null) {
            quarantine(
                    host,
                    "AQE standard job disappeared while Exact Vector input was owned",
                    null);
            return true;
        }
        if (!(currentJob instanceof CraftingIslandJobAccess jobAccess)) {
            quarantine(
                    host,
                    "AdvancedAE job accessor is unavailable during Exact Vector execution",
                    null);
            return true;
        }
        // 再起動後の新しいJobオブジェクトを、保存Task式と一度だけ照合する。
        if (requiresPostLoadValidation) {
            requiresPostLoadValidation = false;
            if (!jobMatchesState(jobAccess)) {
                quarantine(
                        host,
                        "restored AdvancedAE job differs from its Exact Vector receipt",
                        null);
                return true;
            }
        }

        return switch (state.phase()) {
            case PREPARED -> tickPrepared(host, jobAccess);
            case INPUTS_EXTRACTING -> tickInputExtraction(host, jobAccess);
            case INPUTS_ESCROWED -> tickExecutorStart(host, jobAccess);
            case EXECUTOR_ACTIVE, ACCOUNTING ->
                    tickExecutorReceipt(host, jobAccess);
            case ACCOUNTING_COMMITTING, QUARANTINED -> true;
        };
    }

    /**
     * AdvancedAEのcancel前に、外部ReceiptとCPU入力Escrowを安全に戻す。
     *
     * @return vanilla cancelを止める必要がある場合true
     */
    public boolean interceptCancel(AqeStandardVectorHost host) {
        Objects.requireNonNull(host, "host");
        if (state == null) {
            return false;
        }
        // 会計開始後または既に隔離済みなら、vanilla cancelで証拠を消さない。
        if (state.phase()
                        == AqeStandardVectorExecutionState.Phase.ACCOUNTING_COMMITTING
                || state.phase()
                        == AqeStandardVectorExecutionState.Phase.QUARANTINED) {
            return true;
        }
        IGrid grid = host.aco$getStandardVectorCpu().getGrid();
        ExactVectorExecutor executor =
                grid == null ? null : findExecutor(grid, state.executorId());
        if (state.phase()
                        == AqeStandardVectorExecutionState.Phase.EXECUTOR_ACTIVE
                || state.phase()
                        == AqeStandardVectorExecutionState.Phase.ACCOUNTING) {
            // 外部Receiptを先にCANCELLEDへできない場合、CPU入力だけを返してはならない。
            if (executor == null) {
                quarantine(
                        host,
                        "AQE standard Vector cancellation could not be proven by its executor",
                        null);
                return true;
            }
            Optional<VectorTransactionSnapshot> snapshot;
            try {
                executor.cancel(state.plan().transactionId());
                snapshot = executor.snapshot(
                        state.plan().transactionId());
            } catch (RuntimeException | LinkageError cancellationFailure) {
                quarantine(
                        host,
                        "AQE standard Vector cancellation receipt could not be read",
                        cancellationFailure);
                return true;
            }
            /*
             * このstateは既に外部Receipt所有を確認済みなので、Receipt消失や
             * CANCELLED以外の応答を成功と推測しない。
             */
            if (snapshot.isEmpty()
                    || snapshot.orElseThrow().status()
                            != VectorTransactionStatus.CANCELLED) {
                quarantine(
                        host,
                        "AQE standard Vector executor did not persist a cancelled receipt",
                        null);
                return true;
            }
        }
        if (!rollbackInputs(host)) {
            return true;
        }
        state = null;
        host.aco$markStandardVectorDirty();
        return false;
    }

    public void save(CompoundTag owner) {
        Objects.requireNonNull(owner, "owner");
        if (state == null) {
            owner.remove(NBT_STATE);
        } else {
            owner.put(NBT_STATE, state.save());
        }
    }

    public void load(CompoundTag owner) {
        Objects.requireNonNull(owner, "owner");
        if (!owner.contains(NBT_STATE, Tag.TAG_COMPOUND)) {
            state = null;
            requiresPostLoadValidation = false;
            return;
        }
        state = AqeStandardVectorExecutionState.load(
                owner.getCompound(NBT_STATE));
        requiresPostLoadValidation = true;
    }

    public boolean hasUnresolvedState() {
        return state != null;
    }

    private boolean tryPrepare(
            AqeStandardVectorHost host,
            Object currentJob) {
        if (!(currentJob instanceof CraftingIslandJobAccess jobAccess)) {
            throw new IllegalStateException(
                    "AdvancedAE standard Vector job accessor was not applied");
        }
        var prepared = preparePlan(host, jobAccess);
        if (prepared == null) {
            return false;
        }
        IGrid grid = host.aco$getStandardVectorCpu().getGrid();
        if (grid == null) {
            return false;
        }

        ExactVectorExecutor selected = null;
        ExactVectorExecutor retryable = null;
        // 同じGridの全Executorへ一回ずつ事前照会し、最初に全条件を満たす設備を選ぶ。
        for (ExactVectorExecutor executor :
                ExactVectorExecutorRegistry.find(grid)) {
            try {
                VectorExecutionOffer offer =
                        executor.simulate(prepared.plan());
                if (offer.accepted()
                        && offer.durationTicks()
                                == prepared.plan().durationTicks()
                        && offer.physicalThreadSlots() > 0) {
                    selected = executor;
                    break;
                }
                ExactVectorDiagnostics.executorRejected();
                /*
                 * 設備枠や一時資源待ちでは状態を作ってAACを待つ。
                 * 未対応Patternだけは候補にせず、Advanced AE本来の経路へ戻す。
                 */
                if (retryable == null
                        && executor.shouldRetryRejectedOffer(
                                prepared.plan(), offer)) {
                    retryable = executor;
                }
            } catch (RuntimeException | LinkageError failure) {
                // simulateは所有権移転前なので、壊れた候補だけを除外して次の設備を試せる。
                if (ACOConfig.logExactVectorDiagnostics()) {
                    AE2CraftingOptimizer.LOGGER.warn(
                            "Exact Vector executor failed AQE standard simulation",
                            failure);
                }
            }
        }
        // 直ちに受理できる設備がなければ、再試行を明示した最初の設備へ順番待ちする。
        if (selected == null) {
            selected = retryable;
        }
        if (selected == null) {
            return false;
        }
        ExactVectorDiagnostics.planPrepared();
        state = new AqeStandardVectorExecutionState(
                prepared.plan(),
                selected.identity().id(),
                prepared.patternTasks(),
                prepared.internalOutputs());
        host.aco$markStandardVectorDirty();
        return true;
    }

    private PreparedStandardExecution preparePlan(
            AqeStandardVectorHost host,
            CraftingIslandJobAccess jobAccess) {
        GenericStack finalOutput = jobAccess.aco$getIslandFinalOutput();
        long remainingAmount = jobAccess.aco$getIslandRemainingAmount();
        Level level = host.aco$getStandardVectorCpu().getLevel();
        Map<IPatternDetails, Object> liveTasks =
                jobAccess.aco$getIslandTasks();
        // 最終出力、残数、Level、Taskが揃わないJobは既存AdvancedAEへ戻す。
        if (finalOutput == null
                || remainingAmount <= 0L
                || level == null
                || liveTasks == null
                || liveTasks.isEmpty()
                || BigInteger.valueOf(remainingAmount).compareTo(
                                BigInteger.valueOf(
                                        ACOConfig.getExactVectorMinimumExecutions()))
                        < 0) {
            return null;
        }

        Optional<CompiledCraftingIsland<AEKey, IPatternDetails>> compiled =
                Ae2CraftingIslandCompiler.tryCompileWholeDeterministicJob(
                        liveTasks,
                        level,
                        ACOConfig.getExactVectorMaximumPatternNodes(),
                        ACOConfig.getBigIntegerMaximumBits());
        if (compiled.isEmpty()) {
            return null;
        }
        CompiledCraftingIsland<AEKey, IPatternDetails> island =
                compiled.orElseThrow();
        // AdvancedAE CPU Inventoryはlong APIなので、標準Jobでは全境界を無損失longへ限定する。
        if (!island.fitsSignedLongRuntime()) {
            return null;
        }

        AEKey requestedKey = finalOutput.what();
        BigInteger requestedAmount = BigInteger.valueOf(remainingAmount);
        BigInteger rootBoundary = island.boundaryOutputs()
                .getOrDefault(requestedKey, BigInteger.ZERO);
        /*
         * 同じrootの余剰はAdvancedAE insertがRequesterへ送るため、初期実装では
         * 要求量と完全一致するJobだけをVector化し、過剰配送を防ぐ。
         */
        if (!rootBoundary.equals(requestedAmount)) {
            return null;
        }

        List<ExactStack> inputs = exactStacks(island.boundaryInputs());
        List<ExactStack> remainingOutputs = new ArrayList<>();
        // 最終root以外の境界出力だけをCPU側の余剰成果物として保持する。
        for (Map.Entry<AEKey, BigInteger> entry :
                island.boundaryOutputs().entrySet()) {
            if (!entry.getKey().equals(requestedKey)
                    && entry.getValue().signum() > 0) {
                remainingOutputs.add(
                        new ExactStack(entry.getKey(), entry.getValue()));
            }
        }
        List<ExactStack> finals =
                List.of(new ExactStack(requestedKey, requestedAmount));
        int durationTicks = Math.multiplyExact(
                island.criticalPathStages(),
                ACOConfig.getExactVectorTicksPerLogicalStage());
        BigInteger totalEnergy = checkedMagnitude(
                island.logicalExecutions().multiply(
                        ACOConfig.getExactVectorEnergyMicroAePerLogicalExecution()),
                "AQE standard Vector energy");
        long patternGeneration =
                ProviderPatternGenerationTracker.generation();
        long recipeGeneration = RecipeGenerationTracker.generation();
        String fingerprint = VectorPlanFingerprint.create(
                island.fingerprint(),
                requestedAmount,
                inputs,
                mergeOutputs(finals, remainingOutputs));

        List<String> patternIds = new ArrayList<>(island.tasks().size());
        List<AqeStandardVectorExecutionState.PatternTask> patternTasks =
                new ArrayList<>(island.tasks().size());
        // Pattern IDと残回数を一件ずつ保存し、完了時に同じTaskだけを0へする。
        for (CompiledCraftingIsland.Task<AEKey, IPatternDetails> task :
                island.tasks()) {
            long executions = task.executions().longValueExact();
            patternIds.add(task.patternId());
            patternTasks.add(
                    new AqeStandardVectorExecutionState.PatternTask(
                            task.patternId(),
                            executions));
        }
        UUID transactionId = UUID.randomUUID();
        PreparedVectorBatch plan = new PreparedVectorBatch(
                transactionId,
                UUID.randomUUID(),
                VectorResourceMode.HOST_ESCROWED,
                requestedKey,
                requestedAmount,
                island.logicalExecutions(),
                island.criticalPathStages(),
                durationTicks,
                inputs,
                finals,
                List.copyOf(remainingOutputs),
                List.copyOf(patternIds),
                totalEnergy,
                BigInteger.ZERO,
                fingerprint,
                patternGeneration,
                recipeGeneration);
        VectorBatchPlanValidator.validate(
                plan,
                ACOConfig.getBigIntegerMaximumBits(),
                ACOConfig.getExactVectorMaximumPatternNodes(),
                ACOConfig.getExactVectorMaximumInputKeys(),
                ACOConfig.getExactVectorMaximumOutputKeys());
        // Planner中に世代が変わったPlanは、一度もCPU在庫へ触れず破棄する。
        if (patternGeneration
                        != ProviderPatternGenerationTracker.generation()
                || recipeGeneration != RecipeGenerationTracker.generation()) {
            return null;
        }
        return new PreparedStandardExecution(
                plan,
                List.copyOf(patternTasks),
                exactStacks(island.internalOutputs()));
    }

    private boolean tickPrepared(
            AqeStandardVectorHost host,
            CraftingIslandJobAccess jobAccess) {
        if (!jobMatchesState(jobAccess)
                || generationsChanged(state.plan())) {
            state = null;
            host.aco$markStandardVectorDirty();
            return false;
        }
        if (!canAcceptAllOutputs(jobAccess)
                || !hasAllInputs(host.aco$getStandardVectorInventory())) {
            // 境界入力やRequesterがまだ準備できない間は、同じ計画を変更せず待つ。
            return true;
        }
        state.phase(
                AqeStandardVectorExecutionState.Phase.INPUTS_EXTRACTING,
                "extracting AQE CPU boundary inputs");
        host.aco$markStandardVectorDirty();
        return tickInputExtraction(host, jobAccess);
    }

    private boolean tickInputExtraction(
            AqeStandardVectorHost host,
            CraftingIslandJobAccess jobAccess) {
        if (!jobMatchesState(jobAccess)
                || generationsChanged(state.plan())) {
            return fallbackAfterRollback(host);
        }
        ListCraftingInventory inventory =
                host.aco$getStandardVectorInventory();
        long deadline = System.nanoTime()
                + Math.multiplyExact(
                        ACOConfig.getExactVectorGridTimeBudgetMillis(),
                        NANOSECONDS_PER_MILLISECOND);
        int processed = 0;
        // 入力は数量ではなくAEKey単位で進め、soft時間予算へ達したら次tickへ送る。
        while (!state.inputComplete()
                && (processed == 0 || System.nanoTime() < deadline)) {
            ExactStack input = state.currentInput();
            long requested = input.amount().longValueExact();
            long before = inventory.extract(
                    input.key(),
                    Long.MAX_VALUE,
                    Actionable.SIMULATE);
            if (before < requested) {
                return fallbackAfterRollback(host);
            }
            state.pendingOperation(
                    AqeStandardVectorExecutionState.PendingOperation.INPUT_EXTRACT);
            long extracted;
            try {
                extracted = inventory.extract(
                        input.key(),
                        requested,
                        Actionable.MODULATE);
            } catch (RuntimeException failure) {
                quarantine(
                        host,
                        "AQE standard Vector input extraction threw before its receipt was certain",
                        failure);
                return true;
            }
            state.pendingOperation(
                    AqeStandardVectorExecutionState.PendingOperation.NONE);
            long after = inventory.extract(
                    input.key(),
                    Long.MAX_VALUE,
                    Actionable.SIMULATE);
            long observed = before >= after ? before - after : -1L;
            if (extracted != requested
                    || observed != requested) {
                quarantine(
                        host,
                        "AQE standard Vector input extraction changed unexpectedly",
                        null);
                return true;
            }
            state.recordExtractedInput(input);
            processed++;
        }
        if (state.inputComplete()) {
            state.phase(
                    AqeStandardVectorExecutionState.Phase.INPUTS_ESCROWED,
                    "ACO owns all AQE CPU boundary inputs");
        }
        host.aco$markStandardVectorDirty();
        return true;
    }

    private boolean tickExecutorStart(
            AqeStandardVectorHost host,
            CraftingIslandJobAccess jobAccess) {
        if (!jobMatchesState(jobAccess)
                || generationsChanged(state.plan())) {
            return fallbackAfterRollback(host);
        }
        IGrid grid = host.aco$getStandardVectorCpu().getGrid();
        ExactVectorExecutor executor =
                grid == null ? null : findExecutor(grid, state.executorId());
        // 設備が開始前に消えた場合だけ、CPU入力を戻して通常経路へ戻せる。
        if (executor == null) {
            return ACOConfig.exactVectorFallbackBeforeOwnershipTransfer()
                    ? fallbackAfterRollback(host)
                    : true;
        }
        if (ExactVectorExecutorRegistry.activeTransactionCount(grid)
                >= ACOConfig.getExactVectorMaximumActivePerGrid()) {
            // 標準JobとBigInteger親Jobを同じGrid上限へ数え、空きが出るまで入力Escrowを保持する。
            return true;
        }
        // 実際にAAC Receiptを作るtickで、BigInteger親Jobと同じGrid開始予算を消費する。
        if (!ExactVectorGridTickBudget.forGrid(grid).tryStart()) {
            return true;
        }
        VectorExecutionOffer offer;
        try {
            offer = executor.simulate(state.plan());
        } catch (RuntimeException | LinkageError simulationFailure) {
            // simulateはReceipt作成前なので、CPU入力を正確に戻せる。
            if (ACOConfig.logExactVectorDiagnostics()) {
                AE2CraftingOptimizer.LOGGER.warn(
                        "Exact Vector executor failed final AQE standard simulation",
                        simulationFailure);
            }
            return ACOConfig.exactVectorFallbackBeforeOwnershipTransfer()
                    ? fallbackAfterRollback(host)
                    : true;
        }
        if (!offer.accepted()) {
            ExactVectorDiagnostics.executorRejected();
            try {
                // 一時拒否ならCPU入力Escrowを保持し、次tickに同じReceipt作成を再試行する。
                if (executor.shouldRetryRejectedOffer(
                        state.plan(), offer)) {
                    return true;
                }
            } catch (RuntimeException | LinkageError classificationFailure) {
                if (ACOConfig.logExactVectorDiagnostics()) {
                    AE2CraftingOptimizer.LOGGER.warn(
                            "Exact Vector executor failed AQE standard retry classification",
                            classificationFailure);
                }
            }
            return ACOConfig.exactVectorFallbackBeforeOwnershipTransfer()
                    ? fallbackAfterRollback(host)
                    : true;
        }
        if (offer.durationTicks() != state.plan().durationTicks()
                || offer.physicalThreadSlots() <= 0) {
            return ACOConfig.exactVectorFallbackBeforeOwnershipTransfer()
                    ? fallbackAfterRollback(host)
                    : true;
        }
        VectorStartResult started;
        try {
            started = Objects.requireNonNull(
                    executor.start(state.plan()),
                    "Exact Vector executor start result");
        } catch (RuntimeException | LinkageError startFailure) {
            return resolveUncertainStart(
                    host,
                    executor,
                    false,
                    startFailure);
        }
        // 別UUIDの応答は、誤った外部Transactionが動いている可能性を排除できない。
        if (!started.transactionId().equals(
                state.plan().transactionId())) {
            quarantine(
                    host,
                    "Exact Vector executor returned another transaction ID for an AQE standard job",
                    null);
            return true;
        }
        if (!started.started()
                || started.status().terminal()) {
            return resolveUncertainStart(
                    host,
                    executor,
                    started.started(),
                    null);
        }
        state.phase(
                AqeStandardVectorExecutionState.Phase.EXECUTOR_ACTIVE,
                "AAC owns the logical Vector execution receipt");
        host.aco$markStandardVectorDirty();
        return true;
    }

    /**
     * start呼出し後の所有権をReceiptで確定し、空Receiptの場合だけ可逆経路へ戻す。
     */
    private boolean resolveUncertainStart(
            AqeStandardVectorHost host,
            ExactVectorExecutor executor,
            boolean claimedStarted,
            Throwable startFailure) {
        Optional<VectorTransactionSnapshot> snapshot;
        try {
            snapshot = executor.snapshot(
                    state.plan().transactionId());
        } catch (RuntimeException | LinkageError snapshotFailure) {
            if (startFailure != null) {
                snapshotFailure.addSuppressed(startFailure);
            }
            quarantine(
                    host,
                    "AQE standard Vector start outcome could not be proven",
                    snapshotFailure);
            return true;
        }
        if (snapshot.isEmpty()) {
            if (claimedStarted) {
                quarantine(
                        host,
                        "Exact Vector executor reported a started AQE standard job without a receipt",
                        startFailure);
                return true;
            }
            // 同じExecutorがReceiptなしを明示した場合だけ、入力所有権はCPU側に残っている。
            return ACOConfig.exactVectorFallbackBeforeOwnershipTransfer()
                    ? fallbackAfterRollback(host)
                    : true;
        }

        VectorTransactionStatus remote =
                snapshot.orElseThrow().status();
        if (remote == VectorTransactionStatus.CANCELLED) {
            return fallbackAfterRollback(host);
        }
        if (remote == VectorTransactionStatus.QUARANTINED
                || remote == VectorTransactionStatus.COMPLETED) {
            quarantine(
                    host,
                    "AQE standard Vector start returned an unsafe terminal receipt: "
                            + remote,
                    startFailure);
            return true;
        }
        // PREPARED以降のReceiptが存在するため、通常Pattern Pushへ絶対に戻さない。
        state.phase(
                AqeStandardVectorExecutionState.Phase.EXECUTOR_ACTIVE,
                "recovered AQE standard Vector ownership from executor receipt");
        host.aco$markStandardVectorDirty();
        if (startFailure != null) {
            AE2CraftingOptimizer.LOGGER.warn(
                    "Recovered AQE standard Vector start from its persisted executor receipt",
                    startFailure);
        }
        return true;
    }

    private boolean tickExecutorReceipt(
            AqeStandardVectorHost host,
            CraftingIslandJobAccess jobAccess) {
        IGrid grid = host.aco$getStandardVectorCpu().getGrid();
        ExactVectorExecutor executor =
                grid == null ? null : findExecutor(grid, state.executorId());
        // チャンク未読込中はReceipt消失と断定せず、同じ入力Escrowを保持して待つ。
        if (executor == null) {
            return true;
        }
        Optional<VectorTransactionSnapshot> snapshot;
        try {
            snapshot = executor.snapshot(
                    state.plan().transactionId());
        } catch (RuntimeException | LinkageError snapshotFailure) {
            quarantine(
                    host,
                    "AQE standard Vector receipt lookup failed",
                    snapshotFailure);
            return true;
        }
        if (snapshot.isEmpty()) {
            quarantine(
                    host,
                    "registered Exact Vector executor lost the AQE standard receipt",
                    null);
            return true;
        }
        VectorTransactionStatus remote = snapshot.orElseThrow().status();
        return switch (remote) {
            case PREPARED,
                    INPUTS_EXTRACTING,
                    INPUTS_ESCROWED,
                    RUNNING,
                    PAUSED_ENERGY,
                    OUTPUT_PENDING -> true;
            case ACCOUNTING -> {
                state.phase(
                        AqeStandardVectorExecutionState.Phase.ACCOUNTING,
                        "AAC completed the logical critical path");
                host.aco$markStandardVectorDirty();
                yield tickAccounting(host, jobAccess, executor);
            }
            case CANCELLED -> fallbackAfterRollback(host);
            case QUARANTINED -> {
                quarantine(
                        host,
                        "AAC quarantined AQE standard Vector receipt: "
                                + snapshot.orElseThrow().detail(),
                        null);
                yield true;
            }
            case COMPLETED -> {
                /*
                 * AACをCOMPLETEDへできるのはACO会計後だけである。
                 * ローカルstateが残る組合せはcross-chunk保存境界なので再会計しない。
                 */
                quarantine(
                        host,
                        "AAC receipt is complete while AQE standard accounting is unresolved",
                        null);
                yield true;
            }
        };
    }

    private boolean tickAccounting(
            AqeStandardVectorHost host,
            CraftingIslandJobAccess jobAccess,
            ExactVectorExecutor executor) {
        IGrid grid = host.aco$getStandardVectorCpu().getGrid();
        if (grid == null
                || !ExactVectorGridTickBudget.forGrid(grid).tryCompletion()) {
            return true;
        }
        if (!jobMatchesState(jobAccess)) {
            quarantine(
                    host,
                    "AdvancedAE job changed before Exact Vector accounting",
                    null);
            return true;
        }
        if (!canAcceptAllOutputs(jobAccess)) {
            // 出力先の空き不足だけは可逆なBackpressureなので、触れずに次tickで再確認する。
            return true;
        }

        List<Map.Entry<AEKey, Long>> staged = new ArrayList<>();
        try {
            state.pendingOperation(
                    AqeStandardVectorExecutionState.PendingOperation.OUTPUT_ACCOUNTING);
            // 最終rootを最後にstageし、途中失敗ではRequesterへ何も配送しない。
            for (ExactStack output : orderedOutputs()) {
                long amount = output.amount().longValueExact();
                jobAccess.aco$stageIslandOutput(output.key(), amount);
                staged.add(Map.entry(output.key(), amount));
            }
        } catch (RuntimeException stagingFailure) {
            boolean restored = rollbackStagedOutputs(jobAccess, staged);
            state.pendingOperation(
                    AqeStandardVectorExecutionState.PendingOperation.NONE);
            if (!restored) {
                quarantine(
                        host,
                        "AQE standard Vector output staging could not be rolled back",
                        stagingFailure);
            } else {
                state.phase(
                        AqeStandardVectorExecutionState.Phase.ACCOUNTING,
                        "waiting to retry reversible output staging");
                host.aco$markStandardVectorDirty();
            }
            return true;
        }

        /*
         * ここからTask、Requester、CPU Inventoryを変更する。途中失敗は通常経路へ戻さず
         * ACCOUNTING_COMMITTINGの証拠を残して隔離する。
         */
        state.phase(
                AqeStandardVectorExecutionState.Phase.ACCOUNTING_COMMITTING,
                "committing AdvancedAE standard job accounting");
        host.aco$markStandardVectorDirty();
        try {
            Map<IPatternDetails, Object> tasks =
                    jobAccess.aco$getIslandTasks();
            // 保存Receiptと照合済みの全Taskだけを一括完了し、中間Patternを実体化しない。
            for (Map.Entry<IPatternDetails, Object> entry :
                    List.copyOf(tasks.entrySet())) {
                CraftingTaskProgressAccess progress =
                        (CraftingTaskProgressAccess) entry.getValue();
                progress.aco$setTaskProgress(0L);
                tasks.remove(entry.getKey());
            }
            host.aco$notifyStandardVectorTaskChanges();
            // 中間出力ぶんの時間Trackerだけを、AEKey一件につき一度更新する。
            for (ExactStack internal : state.internalOutputs()) {
                jobAccess.aco$decrementIslandInternalOutput(
                        internal.key(),
                        internal.amount().longValueExact());
            }
            // 非最終出力を先にCPUへ入れ、最後のroot insertでAdvancedAE本来のfinishJobを呼ばせる。
            for (ExactStack output : orderedOutputs()) {
                long amount = output.amount().longValueExact();
                long accepted = host.aco$insertStandardVectorOutput(
                        output.key(),
                        amount,
                        Actionable.MODULATE);
                if (accepted != amount) {
                    throw new IllegalStateException(
                            "AdvancedAE accepted a partial Exact Vector output");
                }
            }
            if (!executor.completeAccounting(
                    state.plan().transactionId())) {
                throw new IllegalStateException(
                        "AAC did not acknowledge completed AQE standard accounting");
            }
            state = null;
            host.aco$markStandardVectorDirty();
            return true;
        } catch (RuntimeException accountingFailure) {
            quarantine(
                    host,
                    "AQE standard Vector failed after irreversible accounting began",
                    accountingFailure);
            return true;
        }
    }

    private boolean jobMatchesState(CraftingIslandJobAccess jobAccess) {
        GenericStack finalOutput = jobAccess.aco$getIslandFinalOutput();
        if (finalOutput == null
                || !state.plan().requestedOutput().matches(finalOutput)
                || state.plan().requestedAmount().compareTo(
                                BigInteger.valueOf(
                                        jobAccess.aco$getIslandRemainingAmount()))
                        != 0) {
            return false;
        }
        Map<String, Long> expected = new LinkedHashMap<>();
        // 保存TaskをIDごとの正確なlong残数へ変換する。
        for (AqeStandardVectorExecutionState.PatternTask task :
                state.patternTasks()) {
            expected.put(task.patternId(), task.executions());
        }
        Map<IPatternDetails, Object> live = jobAccess.aco$getIslandTasks();
        if (live.size() != expected.size()) {
            return false;
        }
        // 現在Jobの全Taskが保存ID・残数と一対一で一致することを確認する。
        for (Map.Entry<IPatternDetails, Object> entry : live.entrySet()) {
            if (!(entry.getValue() instanceof CraftingTaskProgressAccess progress)) {
                return false;
            }
            Long amount = expected.get(
                    Ae2CraftingIslandCompiler.patternFingerprint(
                            entry.getKey()));
            if (amount == null
                    || progress.aco$getTaskProgress() != amount) {
                return false;
            }
        }
        return true;
    }

    private boolean hasAllInputs(ListCraftingInventory inventory) {
        // 境界入力をAEKey単位でSIMULATEし、部分抽出を始める前に全量を確認する。
        for (ExactStack input : state.plan().totalInputs()) {
            long amount = input.amount().longValueExact();
            if (inventory.extract(
                            input.key(),
                            amount,
                            Actionable.SIMULATE)
                    != amount) {
                return false;
            }
        }
        return true;
    }

    private boolean canAcceptAllOutputs(
            CraftingIslandJobAccess jobAccess) {
        // Requesterを含む全境界出力が受理可能な時だけ、会計段階へ進む。
        for (ExactStack output : orderedOutputs()) {
            if (!jobAccess.aco$canAcceptIslandOutput(
                    output.key(),
                    output.amount().longValueExact())) {
                return false;
            }
        }
        return true;
    }

    private boolean fallbackAfterRollback(
            AqeStandardVectorHost host) {
        if (!rollbackInputs(host)) {
            return true;
        }
        state = null;
        host.aco$markStandardVectorDirty();
        return false;
    }

    private boolean rollbackInputs(AqeStandardVectorHost host) {
        if (state.extractedInputs().isEmpty()) {
            return true;
        }
        List<Map.Entry<AEKey, BigInteger>> entries =
                new ArrayList<>(state.extractedInputs().entrySet());
        Collections.reverse(entries);
        // 抽出と逆順で、実Receiptに記録したAEKeyと数量だけをCPU Inventoryへ戻す。
        for (Map.Entry<AEKey, BigInteger> entry : entries) {
            state.pendingOperation(
                    AqeStandardVectorExecutionState.PendingOperation.INPUT_ROLLBACK);
            try {
                host.aco$getStandardVectorInventory().insert(
                        entry.getKey(),
                        entry.getValue().longValueExact(),
                        Actionable.MODULATE);
            } catch (RuntimeException rollbackFailure) {
                quarantine(
                        host,
                        "AQE standard Vector input rollback failed",
                        rollbackFailure);
                return false;
            }
            state.pendingOperation(
                    AqeStandardVectorExecutionState.PendingOperation.NONE);
            state.recordRolledBackInput(
                    entry.getKey(),
                    entry.getValue());
        }
        host.aco$markStandardVectorDirty();
        return true;
    }

    private List<ExactStack> orderedOutputs() {
        List<ExactStack> outputs = new ArrayList<>(
                state.plan().remainingOutputs().size()
                        + state.plan().finalOutputs().size());
        outputs.addAll(state.plan().remainingOutputs());
        outputs.addAll(state.plan().finalOutputs());
        return List.copyOf(outputs);
    }

    private static boolean rollbackStagedOutputs(
            CraftingIslandJobAccess jobAccess,
            List<Map.Entry<AEKey, Long>> staged) {
        boolean complete = true;
        // stageと逆順でwaitingForを戻し、配送前の会計状態へ復元する。
        for (int index = staged.size() - 1; index >= 0; index--) {
            Map.Entry<AEKey, Long> entry = staged.get(index);
            try {
                jobAccess.aco$unstageIslandOutput(
                        entry.getKey(),
                        entry.getValue());
            } catch (RuntimeException rollbackFailure) {
                complete = false;
            }
        }
        return complete;
    }

    private static ExactVectorExecutor findExecutor(
            IGrid grid,
            String executorId) {
        // 稼働枠が満杯でも既存Receiptを追跡できるregistered一覧から同じ設備を探す。
        for (ExactVectorExecutor executor :
                ExactVectorExecutorRegistry.findRegistered(grid)) {
            if (executor.identity().id().equals(executorId)) {
                return executor;
            }
        }
        return null;
    }

    private void quarantine(
            AqeStandardVectorHost host,
            String reason,
            Throwable failure) {
        if (state != null) {
            state.quarantine(reason);
        }
        host.aco$markStandardVectorDirty();
        if (failure == null) {
            AE2CraftingOptimizer.LOGGER.error(reason);
        } else {
            AE2CraftingOptimizer.LOGGER.error(reason, failure);
        }
    }

    private static boolean generationsChanged(
            PreparedVectorBatch plan) {
        return plan.patternGeneration()
                        != ProviderPatternGenerationTracker.generation()
                || plan.recipeGeneration()
                        != RecipeGenerationTracker.generation();
    }

    private static List<ExactStack> exactStacks(
            Map<AEKey, BigInteger> counts) {
        List<ExactStack> result = new ArrayList<>(counts.size());
        // LinkedHashMap順の各正数差分を一件のExactStackへ変換する。
        for (Map.Entry<AEKey, BigInteger> entry : counts.entrySet()) {
            if (entry.getValue().signum() > 0) {
                result.add(
                        new ExactStack(entry.getKey(), entry.getValue()));
            }
        }
        return List.copyOf(result);
    }

    private static List<ExactStack> mergeOutputs(
            List<ExactStack> finals,
            List<ExactStack> remaining) {
        Map<AEKey, BigInteger> merged = new LinkedHashMap<>();
        // Fingerprint上は最終出力と余剰出力をAEKey単位の合計へまとめる。
        for (ExactStack output : finals) {
            merged.merge(output.key(), output.amount(), BigInteger::add);
        }
        for (ExactStack output : remaining) {
            merged.merge(output.key(), output.amount(), BigInteger::add);
        }
        return exactStacks(merged);
    }

    private static BigInteger checkedMagnitude(
            BigInteger value,
            String name) {
        if (value.signum() < 0
                || value.bitLength()
                        > ACOConfig.getBigIntegerMaximumBits()) {
            throw new ArithmeticException(
                    name + " exceeds ACO BigInteger limit");
        }
        return value;
    }

    private record PreparedStandardExecution(
            PreparedVectorBatch plan,
            List<AqeStandardVectorExecutionState.PatternTask> patternTasks,
            List<ExactStack> internalOutputs) {
        private PreparedStandardExecution {
            Objects.requireNonNull(plan, "plan");
            patternTasks = List.copyOf(patternTasks);
            internalOutputs = List.copyOf(internalOutputs);
        }
    }
}

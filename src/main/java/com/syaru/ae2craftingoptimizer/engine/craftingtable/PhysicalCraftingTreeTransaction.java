package com.syaru.ae2craftingoptimizer.engine.craftingtable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.api.batch.ExactPatternFormula;
import com.syaru.ae2craftingoptimizer.api.batch.v2.ProviderOwnedPatternBatchTarget;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchMode;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchRequest;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchTarget;
import com.syaru.ae2craftingoptimizer.api.vector.ExactCraftingStep;
import com.syaru.ae2craftingoptimizer.api.vector.ExactStack;
import com.syaru.ae2craftingoptimizer.api.vector.ExactStorageMutationResult;
import com.syaru.ae2craftingoptimizer.api.vector.ExactVectorStorageService;
import com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatch;
import com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatchCodec;
import com.syaru.ae2craftingoptimizer.engine.Ae2CompiledCraftingGraphCache;
import com.syaru.ae2craftingoptimizer.scheduler.PatternProviderRoutingCache;
import com.syaru.ae2craftingoptimizer.util.StableFingerprint;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * BigInteger親注文を、依存順に実行されるNeoECO作業台仕事として所有する正本状態。
 *
 * <p>InsaneAEの「一回だけ実assembleして係数を掛ける」方式と、NeoECOの
 * Pattern Bus・Worker・Thread・電力・物理進捗を組み合わせる。各段の実出力は
 * 永続Escrowへ入った後でのみ次段の入力として使えるため、最終成果物を直接変換しない。</p>
 *
 * <p>注文数量はBigInteger係数であり、処理回数にはしない。同じ20段レシピなら
 * 1個でもLong.MAX_VALUE個でも物理依存段数は20段のままである。</p>
 */
public final class PhysicalCraftingTreeTransaction {
    public static final String ENGINE_ID =
            "aco:physical-crafting-table-tree-v4";
    private static final int SCHEMA_VERSION = 2;
    /** 一つの物理レシピ段をGUI進捗へ換算する固定単位。 */
    private static final int PROGRESS_UNITS_PER_STEP = 100;
    /** 破損NBTによる巨大なキー配列確保を防ぐ固定上限。 */
    private static final int MAXIMUM_EXACT_KEYS = 65_536;
    /** 保存する診断文字列の固定上限。 */
    private static final int MAXIMUM_DETAIL_LENGTH = 2_048;

    private final PreparedVectorBatch plan;
    private final ExactCraftingEscrow<AEKey> escrow;
    private final List<StepReceipt> steps;
    private State state;
    private int inputCursor;
    private int outputCursor;
    private int schedulerCursor;
    private long validatedPatternGeneration;
    private long validatedRecipeGeneration;
    private PendingNetworkBatchMutation pendingNetworkMutation;
    private boolean cancellationRequested;
    private String detail;
    /** 直前のtickで実際に設備処理または会計を試みた物理段数。 */
    private int lastConsumedOperations;
    /**
     * 現在のProvider/Recipe世代で解決済みの一回分Pattern式。
     *
     * <p>Pattern実体をNBTへ保存せず、ロード後または世代変更後に一度だけ再構築する。</p>
     */
    private List<ResolvedStep> resolvedStepCache;

    private PhysicalCraftingTreeTransaction(
            PreparedVectorBatch plan,
            Map<AEKey, BigInteger> escrow,
            List<StepReceipt> steps,
            State state,
            int inputCursor,
            int outputCursor,
            int schedulerCursor,
            long validatedPatternGeneration,
            long validatedRecipeGeneration,
            PendingNetworkBatchMutation pendingNetworkMutation,
            boolean cancellationRequested,
            String detail) {
        this.plan =
                Objects.requireNonNull(
                        plan,
                        "plan");
        this.escrow =
                new ExactCraftingEscrow<>(
                        checkedCounts(
                                escrow,
                                "escrow",
                                true));
        this.steps =
                new ArrayList<>(
                        checkedReceipts(
                                plan,
                                steps));
        this.state =
                Objects.requireNonNull(
                        state,
                        "state");
        this.inputCursor =
                inputCursor;
        this.outputCursor =
                outputCursor;
        this.schedulerCursor =
                schedulerCursor;
        this.validatedPatternGeneration =
                validatedPatternGeneration;
        this.validatedRecipeGeneration =
                validatedRecipeGeneration;
        this.pendingNetworkMutation =
                pendingNetworkMutation;
        this.cancellationRequested =
                cancellationRequested;
        this.detail =
                checkedDetail(
                        detail);
        this.resolvedStepCache =
                List.of();
        this.lastConsumedOperations =
                0;
        validateState();
    }

    public static PhysicalCraftingTreeTransaction create(
            PreparedVectorBatch plan) {
        Objects.requireNonNull(
                plan,
                "plan");
        // 実Pattern列のない旧計画は、数量や依存関係を推測して実行しない。
        if (plan.craftingSteps()
                .isEmpty()) {
            throw new IllegalArgumentException(
                    "physical crafting-table plan has no recipe steps");
        }
        List<StepReceipt> receipts =
                new ArrayList<>(
                        plan.craftingSteps()
                                .size());
        // 固有Pattern一件につき、再起動後も変わらない物理Transaction IDを割り当てる。
        for (int index = 0;
                index < plan.craftingSteps()
                        .size();
                index++) {
            ExactCraftingStep step =
                    plan.craftingSteps()
                            .get(
                                    index);
            UUID transactionId =
                    UUID.randomUUID();
            receipts.add(
                    StepReceipt.waiting(
                            index,
                            transactionId,
                            stepDigest(
                                    plan,
                                    step,
                                    index,
                                    transactionId)));
        }
        return new PhysicalCraftingTreeTransaction(
                plan,
                Map.of(),
                receipts,
                State.VALIDATING,
                0,
                0,
                0,
                -1L,
                -1L,
                null,
                false,
                "");
    }

    public static PhysicalCraftingTreeTransaction load(
            CompoundTag owner) {
        Objects.requireNonNull(
                owner,
                "owner");
        /*
         * 旧「全Patternを証明して最終出力を直接生成」方式は意味が異なる。
         * schema移行で出力を推測すると複製し得るため、v4だけを復元する。
         */
        if (owner.getInt(
                            "schema")
                        != SCHEMA_VERSION
                || !owner.contains(
                        "plan",
                        Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException(
                    "unsupported physical crafting-tree transaction schema");
        }
        PreparedVectorBatch plan =
                PreparedVectorBatchCodec.decode(
                        owner.getCompound(
                                "plan"));
        ListTag encodedSteps =
                requireCompoundList(
                        owner,
                        "steps",
                        plan.craftingSteps()
                                .size());
        List<StepReceipt> receipts =
                new ArrayList<>(
                        encodedSteps.size());
        // 保存順と計画上のStep indexを一対一で復元する。
        for (int index = 0;
                index < encodedSteps.size();
                index++) {
            receipts.add(
                    StepReceipt.load(
                            encodedSteps.getCompound(
                                    index)));
        }
        PendingNetworkBatchMutation pending =
                owner.contains(
                                "pendingNetworkMutation",
                                Tag.TAG_COMPOUND)
                        ? PendingNetworkBatchMutation.load(
                                owner.getCompound(
                                        "pendingNetworkMutation"))
                        : null;
        return new PhysicalCraftingTreeTransaction(
                plan,
                decodeCounts(
                        owner,
                        "escrow"),
                receipts,
                parseState(
                        owner.getString(
                                "state")),
                owner.getInt(
                        "inputCursor"),
                owner.getInt(
                        "outputCursor"),
                owner.getInt(
                        "schedulerCursor"),
                owner.getLong(
                        "validatedPatternGeneration"),
                owner.getLong(
                        "validatedRecipeGeneration"),
                pending,
                owner.getBoolean(
                        "cancellationRequested"),
                owner.getString(
                        "detail"));
    }

    public CompoundTag save() {
        CompoundTag owner =
                new CompoundTag();
        owner.putInt(
                "schema",
                SCHEMA_VERSION);
        owner.put(
                "plan",
                PreparedVectorBatchCodec.encode(
                        plan));
        owner.putString(
                "state",
                state.name());
        owner.putInt(
                "inputCursor",
                inputCursor);
        owner.putInt(
                "outputCursor",
                outputCursor);
        owner.putInt(
                "schedulerCursor",
                schedulerCursor);
        owner.putLong(
                "validatedPatternGeneration",
                validatedPatternGeneration);
        owner.putLong(
                "validatedRecipeGeneration",
                validatedRecipeGeneration);
        owner.put(
                "escrow",
                encodeCounts(
                        escrow.snapshot()));
        owner.putString(
                "detail",
                detail);
        // 適用前のME境界操作を親NBTへ先に保存し、停止後にbefore/afterを再照合する。
        if (pendingNetworkMutation != null) {
            owner.put(
                    "pendingNetworkMutation",
                    pendingNetworkMutation.save());
        }
        // 境界操作の照合完了後に取消へ移る要求だけを、停止をまたいで保持する。
        if (cancellationRequested) {
            owner.putBoolean(
                    "cancellationRequested",
                    true);
        }
        ListTag encodedSteps =
                new ListTag();
        // NBT件数は注文数量ではなく固有Pattern数にだけ比例する。
        for (StepReceipt receipt :
                steps) {
            encodedSteps.add(
                    receipt.save());
        }
        owner.put(
                "steps",
                encodedSteps);
        return owner;
    }

    /**
     * 一tickで固有Pattern数に比例する範囲だけを進める。
     *
     * @param operationBudget このtickに確認できる物理レシピ段数
     */
    public TickOutcome tick(
            IGrid grid,
            Level level,
            IActionSource source,
            Ae2CompiledCraftingGraphCache.Snapshot snapshot,
            int operationBudget) {
        Objects.requireNonNull(
                grid,
                "grid");
        Objects.requireNonNull(
                level,
                "level");
        Objects.requireNonNull(
                source,
                "source");
        Objects.requireNonNull(
                snapshot,
                "snapshot");
        // 0以下の予算は呼出側の不具合なので、状態を曖昧にせず拒否する。
        if (operationBudget <= 0) {
            throw new IllegalArgumentException(
                    "operationBudget must be positive");
        }
        // 各tickの実消費数を0から数え直し、依存待ちの未使用Claimを呼出側へ返せるようにする。
        lastConsumedOperations =
                0;
        try {
            /*
             * ME境界操作は、親NBTへpendingを保存した次のtickにだけ適用する。
             * 停止後もbefore/afterを見て、同じ操作を二度行わない。
             */
            if (pendingNetworkMutation != null) {
                return applyPendingNetworkMutation(
                        grid,
                        source);
            }
            /*
             * prepare済み境界操作がなくなった後でだけ取消へ移る。
             * これより前に状態を変えると、再起動時にpendingの用途を照合できない。
             */
            if (cancellationRequested) {
                beginCancellation();
                return TickOutcome.changed();
            }
            return switch (state) {
                case VALIDATING ->
                        validateAndBegin(
                                snapshot,
                                level);
                case RESERVING_BOUNDARY_INPUTS ->
                        reserveNextBoundaryInput(
                                grid,
                                source);
                case EXECUTING_RECIPES ->
                        advancePhysicalRecipes(
                                grid,
                                level,
                                snapshot,
                                operationBudget);
                case RETURNING_RESULTS ->
                        returnNextResult(
                                grid,
                                source);
                case CANCELLING_THREADS ->
                        advanceCancellation(
                                level,
                                snapshot,
                                operationBudget);
                case RETURNING_CANCELLED_ESCROW ->
                        returnNextCancelledStack(
                                grid,
                                source);
                case COMPLETE ->
                        TickOutcome.complete();
                case CANCELLED ->
                        TickOutcome.cancelled();
                case QUARANTINED ->
                        TickOutcome.quarantined(
                                detail);
            };
        } catch (RuntimeException | LinkageError failure) {
            quarantine(
                    "physical crafting-tree transaction failed: "
                            + failure);
            return TickOutcome.quarantined(
                    detail);
        }
    }

    /**
     * 最終成果物のME返却開始前だけ取消を受理する。
     *
     * <p>取消後は、完成済み中間素材と未消費入力をEscrowからMEへ全て返す。</p>
     */
    public boolean requestCancellation() {
        // 受付済みまたは取消完了なら、同じ要求を冪等に成功扱いする。
        if (state == State.CANCELLING_THREADS
                || state == State.RETURNING_CANCELLED_ESCROW
                || state == State.CANCELLED
                || cancellationRequested) {
            return true;
        }
        // 成果物返却開始後と隔離後は、完全な逆操作を証明できないため拒否する。
        if (state == State.RETURNING_RESULTS
                || state == State.COMPLETE
                || state == State.QUARANTINED) {
            return false;
        }
        /*
         * 保存済み境界操作がある間は親状態を維持する。
         * 操作が未適用か適用済みかを照合した後、次tickで取消へ移る。
         */
        if (pendingNetworkMutation != null) {
            cancellationRequested =
                    true;
            detail =
                    "cancellation waits for pending storage reconciliation";
            return true;
        }
        beginCancellation();
        return true;
    }

    /** 外部会計との不一致時に、物理所有権を推測で完了・取消せず隔離する。 */
    public void quarantineForAccounting(String reason) {
        quarantine(reason);
    }

    private void beginCancellation() {
        cancellationRequested =
                false;
        state =
                State.CANCELLING_THREADS;
        detail =
                "cancellation requested";
    }

    public PreparedVectorBatch plan() {
        return plan;
    }

    public UUID transactionId() {
        return plan.transactionId();
    }

    public State state() {
        return state;
    }

    public int lastConsumedOperations() {
        return lastConsumedOperations;
    }

    public Map<AEKey, BigInteger> escrowSnapshot() {
        return escrow.snapshot();
    }

    /**
     * Advanced AEの実Jobへ投影する、物理Receipt由来の絶対会計Snapshot。
     *
     * <p>差分ではなくTransaction開始時からの累積値を返す。同じ保存Receiptを再起動後に
     * 再照合しても、Pattern taskやwaitingForを二重に減らさない。</p>
     */
    public AccountingSnapshot accountingSnapshot(
            Ae2CompiledCraftingGraphCache.Snapshot snapshot,
            Level level) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(level, "level");
        Map<String, BigInteger> plannedTasks = new LinkedHashMap<>();
        Map<String, BigInteger> dispatchedTasks = new LinkedHashMap<>();
        Map<AEKey, BigInteger> expectedOutputs = new LinkedHashMap<>();
        Map<AEKey, BigInteger> introducedOutputs = new LinkedHashMap<>();
        Map<AEKey, BigInteger> creditedOutputs = new LinkedHashMap<>();
        // 固有Pattern数だけを一巡し、注文数量ぶんの会計要素は作らない。
        for (StepReceipt receipt : steps) {
            ResolvedStep resolved = resolveStep(
                    snapshot,
                    level,
                    receipt.index());
            String patternId = resolved.step().patternId();
            BigInteger executions = resolved.step().executions();
            mergePositive(
                    plannedTasks,
                    patternId,
                    executions);
            resolved.expectedOutputs().forEach((key, amount) ->
                    mergePositive(expectedOutputs, key, amount));
            // Pattern Busが一度でも仕事を所有した段だけ、通常AE2のtask減算へ反映する。
            if (receipt.dispatched()) {
                mergePositive(
                        dispatchedTasks,
                        patternId,
                        executions);
                // 通常AE2と同じく、Pattern投入時点でだけそのPatternの全出力をwaitingForへ加える。
                resolved.expectedOutputs().forEach((key, amount) ->
                        mergePositive(introducedOutputs, key, amount));
            }
            // 実出力をEscrowへ一度会計した段だけ、通常AE2のwaitingForから差し引く。
            if (receipt.outputCredited()) {
                resolved.expectedOutputs().forEach((key, amount) ->
                        mergePositive(creditedOutputs, key, amount));
            }
        }
        boolean finalOutputReturned =
                outputCursor == expectedFinalOutputs().size()
                        && (state == State.RETURNING_RESULTS
                                || state == State.COMPLETE);
        return new AccountingSnapshot(
                plannedTasks,
                dispatchedTasks,
                expectedOutputs,
                introducedOutputs,
                creditedOutputs,
                finalOutputReturned);
    }

    /** GUIへ渡す進捗は、実NeoECO Threadと完了済み物理段から求める。 */
    public int progressNumerator() {
        int total =
                progressDenominator();
        // 全レシピが実完了した後のME返却中は、物理進捗を100%として表示する。
        if (state == State.RETURNING_RESULTS
                || state == State.COMPLETE) {
            return total;
        }
        int completed =
                0;
        // 各Thread進捗を100単位へ正規化し、注文数量ではなく固有Pattern数だけを合算する。
        for (StepReceipt receipt :
                steps) {
            completed =
                    Math.addExact(
                            completed,
                            receipt.normalizedProgress());
        }
        return Math.min(
                total,
                completed);
    }

    public int progressDenominator() {
        return Math.max(
                1,
                Math.multiplyExact(
                        steps.size(),
                        PROGRESS_UNITS_PER_STEP));
    }

    private TickOutcome validateAndBegin(
            Ae2CompiledCraftingGraphCache.Snapshot snapshot,
            Level level) {
        /*
         * 境界素材へ触る前に、材料側から完成品側までの全式と依存順を
         * 仮想Escrowで一度だけ証明する。実行時は同じ式を再探索しない。
         */
        if (!validateCompiledTree(
                snapshot,
                level)) {
            quarantine(
                    "compiled crafting-table tree does not conserve its exact inputs and outputs");
            return TickOutcome.quarantined(
                    detail);
        }
        validatedPatternGeneration =
                snapshot.graph()
                        .generation();
        validatedRecipeGeneration =
                snapshot.recipeGeneration();
        state =
                State.RESERVING_BOUNDARY_INPUTS;
        detail =
                "";
        return TickOutcome.changed();
    }

    private TickOutcome reserveNextBoundaryInput(
            IGrid grid,
            IActionSource source) {
        List<ExactStack> inputs =
                plan.totalInputs();
        // 全境界素材が一括Escrowへ入った後のtickからだけ、物理レシピを解禁する。
        if (inputCursor >= inputs.size()) {
            state =
                    State.EXECUTING_RECIPES;
            detail =
                    "";
            return TickOutcome.changed();
        }
        return prepareNetworkMutationBatch(
                grid,
                source,
                NetworkDirection.EXTRACT,
                MutationPurpose.BOUNDARY_INPUT,
                countsFromStacks(
                        inputs));
    }

    private TickOutcome advancePhysicalRecipes(
            IGrid grid,
            Level level,
            Ae2CompiledCraftingGraphCache.Snapshot snapshot,
            int operationBudget) {
        /*
         * Providerまたはレシピ世代が変わった場合だけ、保存式を全体再検証する。
         * 不一致なら新しい式へ勝手に差し替えず、取消経路へ移る。
         */
        if (!isValidatedFor(
                        snapshot)
                && !validateCompiledTree(
                        snapshot,
                        level)) {
            state =
                    State.CANCELLING_THREADS;
            detail =
                    "crafting-table recipe generation changed during execution";
            return TickOutcome.changed();
        }
        validatedPatternGeneration =
                snapshot.graph()
                        .generation();
        validatedRecipeGeneration =
                snapshot.recipeGeneration();

        int consumedOperations =
                0;
        int changed =
                0;
        int stepCount =
                steps.size();
        int lastProcessedIndex =
                -1;
        /*
         * 物理Thread所有中、投入準備済み、依存入力が揃った段の順に処理する。
         * 各Laneでは保存Cursorから巡回し、独立枝同士の公平性を維持する。
         */
        scheduling:
        for (SchedulingLane lane :
                SchedulingLane.RUNNABLE_ORDER) {
            // 同じLaneの全段を一巡し、注文数量ではなく固有Pattern数だけを確認する。
            for (int offset = 0;
                    offset < stepCount;
                    offset++) {
                // 実際に処理した段数がGridから借りた上限へ達したら、残りを次tickへ送る。
                if (consumedOperations >= operationBudget) {
                    break scheduling;
                }
                int index =
                        Math.floorMod(
                                schedulerCursor
                                        + offset,
                                stepCount);
                StepReceipt receipt =
                        steps.get(
                                index);
                // 現在の状態を担当しないLaneでは設備やレシピ解決へ触れない。
                if (schedulingLane(
                                receipt.state())
                        != lane) {
                    continue;
                }
                ResolvedStep resolved =
                        resolveStep(
                                snapshot,
                                level,
                                receipt.index());
                /*
                 * 中間素材が未完成の親段は走査だけで待機させる。
                 * この判定は設備I/Oを行わないため、Gridの実行予算を消費しない。
                 */
                if (lane == SchedulingLane.DEPENDENCY_READY
                        && !escrow.containsAll(
                                resolved.inputTotals())) {
                    continue;
                }
                consumedOperations++;
                lastConsumedOperations =
                        consumedOperations;
                lastProcessedIndex =
                        index;
                StepAdvance advance =
                        advanceOneRecipe(
                                grid,
                                level,
                                receipt,
                                resolved);
                // 一段でも状態が変われば、親NBTをdirtyにする結果を返す。
                if (advance.changed()) {
                    changed++;
                }
                // 物理所有権が不確定な一段を見つけたら、他段を進めず親全体を隔離する。
                if (advance.quarantined()) {
                    quarantine(
                            advance.detail());
                    return TickOutcome.quarantined(
                            detail);
                }
            }
        }
        // 一段以上を処理した場合だけ、次回の同一Lane開始位置を一つ先へ進める。
        if (lastProcessedIndex >= 0) {
            schedulerCursor =
                    Math.floorMod(
                            lastProcessedIndex + 1,
                            stepCount);
        }

        // 全段の実出力がEscrowへ入り、Thread解放まで終わった後だけME返却へ進む。
        if (allRecipesAcknowledged()) {
            Map<AEKey, BigInteger> expected =
                    expectedFinalOutputs();
            // 最終Escrowが数式上の結果と完全一致しなければ、直接補正せず隔離する。
            if (!escrow.snapshot()
                    .equals(
                            expected)) {
                quarantine(
                        "physical recipe outputs do not equal the planned final inventory");
                return TickOutcome.quarantined(
                        detail);
            }
            state =
                    State.RETURNING_RESULTS;
            detail =
                    "";
            return TickOutcome.changed();
        }
        return changed > 0
                ? TickOutcome.changed()
                : TickOutcome.waiting(
                        detail.isBlank()
                                ? "waiting for recipe dependencies or NeoECO workers"
                                : detail);
    }

    private StepAdvance advanceOneRecipe(
            IGrid grid,
            Level level,
            StepReceipt receipt,
            ResolvedStep resolved) {
        // 次段の全入力がEscrowに実在する時だけ、一括予約して物理仕事を開始する。
        if (receipt.state()
                == StepState.WAITING_FOR_INPUTS) {
            // 依存する中間出力が未完成なら、親段を触らず待機する。
            if (!escrow.containsAll(
                    resolved.inputTotals())) {
                return StepAdvance.waiting();
            }
            escrow.debitExact(
                    resolved.inputTotals());
            receipt.reserveInputs(
                    resolved.inputTotals());
            detail =
                    "";
        }

        /*
         * 入力予約とTarget選択は外部副作用を持たないため同じtickで続行する。
         * 停止前にTargetが受理済みなら、全候補の所有権照合で同じ仕事へ復帰する。
         */
        if (receipt.state()
                == StepState.INPUTS_RESERVED) {
            SelectedTarget selected =
                    selectTarget(
                            grid,
                            resolved.pattern(),
                            receipt.routeCursor(),
                            receipt.transactionId(),
                            receipt.payloadDigest());
            // 現在利用できる実設備がない場合も、予約入力をEscrow外で保持して待つ。
            if (selected == null) {
                detail =
                        "waiting for a NeoECO crafting-table Pattern Bus";
                return StepAdvance.waiting();
            }
            receipt.selectTarget(
                    selected.position(),
                    selected.nextRouteCursor());
        }

        BlockPos targetPosition =
                BlockPos.of(
                        Objects.requireNonNull(
                                receipt.targetPosition(),
                                "selected target position"));
        // チャンクを強制ロードせず、形成済み設備が自然に戻るまで待つ。
        if (!level.isLoaded(
                targetPosition)) {
            detail =
                    "physical crafting target is not loaded";
            return StepAdvance.waiting();
        }
        BlockEntity targetEntity =
                level.getBlockEntity(
                        targetPosition);
        /*
         * 対象ブロックがBlock Entityを持つのに実体だけ未ロードなら、所有権消失と決めつけない。
         * air化または別Block Entityへの置換が確定した場合だけ、ACO Escrowから再配送する。
         */
        if (targetEntity == null
                && level.getBlockState(
                                targetPosition)
                        .hasBlockEntity()) {
            detail =
                    "physical crafting target block entity is still loading";
            return StepAdvance.waiting();
        }
        // AAC設備が撤去・置換された場合は、正確な保存状態から数量非依存で復旧する。
        if (!(targetEntity
                instanceof CraftingTableBatchTarget target)) {
            return recoverRemovedTarget(
                    resolved,
                    receipt);
        }

        // 保存済み入力全量と一回分レシピ式を、一つの物理Threadへ渡す。
        if (receipt.state()
                == StepState.TARGET_SELECTED) {
            CraftingTableBatchRequest request =
                    request(
                            resolved,
                            receipt);
            boolean accepted =
                    target.aco$acceptCraftingTableBatch(
                            request);
            boolean alreadyOwned =
                    target.aco$ownsCraftingTableBatch(
                            receipt.transactionId(),
                            receipt.payloadDigest());
            // 停止直後の再送は、同じTransactionを既に所有していれば成功扱いにする。
            if (accepted
                    || alreadyOwned) {
                receipt.accept();
                detail =
                        "";
                return StepAdvance.updated();
            }
            /*
             * 現Targetが満杯なら入力予約を維持したまま別候補へ回す。
             * Transaction IDは変えないため、二重所有を次回も検出できる。
             */
            receipt.retryAnotherTarget();
            detail =
                    "all NeoECO crafting-table workers are busy";
            return StepAdvance.updated();
        }

        // 受理済みThreadの実進捗と、実assembleから得た出力Receiptを読む。
        if (receipt.state()
                == StepState.ACCEPTED) {
            Optional<CraftingTableBatchSnapshot> physical =
                    target.aco$craftingTableBatchSnapshot(
                            receipt.transactionId(),
                            receipt.payloadDigest());
            // 所有中ならSnapshot生成を待ち、所有を失った時だけ別Targetへ再投入する。
            if (physical.isEmpty()) {
                // 同じTargetがまだ所有している間は、二重投入せず待つ。
                if (target.aco$ownsCraftingTableBatch(
                        receipt.transactionId(),
                        receipt.payloadDigest())) {
                    detail =
                            "waiting for NeoECO Thread snapshot";
                    return StepAdvance.waiting();
                }
                receipt.retryAnotherTarget();
                detail =
                        "NeoECO Thread was released before output";
                return StepAdvance.updated();
            }
            CraftingTableBatchSnapshot physicalState =
                    physical.orElseThrow();
            boolean progressChanged =
                    receipt.updateProgress(
                            physicalState.progress(),
                            physicalState.maximumProgress(),
                            physicalState.detail());
            return switch (physicalState.state()) {
                case RUNNING ->
                        progressChanged
                                ? StepAdvance.updated()
                                : StepAdvance.waiting();
                case OUTPUT_READY -> {
                    /*
                     * 実assemble一回分×係数が計画式と一致する場合だけ、
                     * 同tickでEscrowへ会計し、依存する次段を解禁する。
                     * TargetはThread解放前に同じWorker NBTへ終端Receiptを残す。
                     */
                    if (!physicalState.exactOutputs()
                            .equals(
                                    resolved.expectedOutputs())) {
                        yield StepAdvance.quarantined(
                                "NeoECO physical output differs from the compiled formula");
                    }
                    receipt.observeOutput(
                            physicalState.exactOutputs(),
                            physicalState.detail());
                    escrow.credit(
                            receipt.observedOutputs());
                    receipt.creditOutput();
                    /*
                     * acknowledge失敗時もOUTPUT_CREDITEDを保存する。
                     * 次tickは実Threadまたは終端Receiptへ冪等に再送する。
                     */
                    target.aco$acknowledgeCraftingTableBatch(
                            receipt.transactionId(),
                            receipt.payloadDigest());
                    yield StepAdvance.updated();
                }
                case ACKNOWLEDGED -> {
                    /*
                     * Worker側だけ先に保存された停止復旧。
                     * 終端Receiptの実出力を一度だけEscrowへ会計し、次tickにforgetする。
                     */
                    if (!physicalState.exactOutputs()
                            .equals(
                                    resolved.expectedOutputs())) {
                        yield StepAdvance.quarantined(
                                "NeoECO terminal receipt differs from the compiled formula");
                    }
                    receipt.observeOutput(
                            physicalState.exactOutputs(),
                            physicalState.detail());
                    escrow.credit(
                            receipt.observedOutputs());
                    receipt.creditOutput();
                    yield StepAdvance.updated();
                }
                case CANCELLED -> {
                    receipt.retryAnotherTarget();
                    yield StepAdvance.updated();
                }
                case QUARANTINED ->
                        StepAdvance.quarantined(
                                physicalState.detail());
            };
        }

        // 旧schemaのOUTPUT_OBSERVEDだけを、一度の会計で新しいOUTPUT_CREDITEDへ移す。
        if (receipt.state()
                == StepState.OUTPUT_OBSERVED) {
            // 保存出力が現在式と違う場合は、レシピ変更として補正せず隔離する。
            if (!receipt.observedOutputs()
                    .equals(
                            resolved.expectedOutputs())) {
                return StepAdvance.quarantined(
                        "saved physical output no longer matches the recipe formula");
            }
            escrow.credit(
                    receipt.observedOutputs());
            receipt.creditOutput();
            target.aco$acknowledgeCraftingTableBatch(
                    receipt.transactionId(),
                    receipt.payloadDigest());
            detail =
                    "";
            return StepAdvance.updated();
        }

        /*
         * Escrow反映済み状態が親NBTに保存された次tickでだけ終端Receiptを破棄する。
         * Receiptが既に破棄済みでも、外部所有権がなければ冪等に完了できる。
         */
        if (receipt.state()
                == StepState.OUTPUT_CREDITED) {
            Optional<CraftingTableBatchSnapshot> physical =
                    target.aco$craftingTableBatchSnapshot(
                            receipt.transactionId(),
                            receipt.payloadDigest());
            // ThreadがまだOUTPUT_READYなら、終端Receipt作成と解放を冪等に再送する。
            if (physical.isPresent()
                    && physical.orElseThrow()
                                    .state()
                            == CraftingTableBatchSnapshot.State
                                    .OUTPUT_READY) {
                target.aco$acknowledgeCraftingTableBatch(
                        receipt.transactionId(),
                        receipt.payloadDigest());
                physical =
                        target.aco$craftingTableBatchSnapshot(
                                receipt.transactionId(),
                                receipt.payloadDigest());
            }
            // 終端Receiptの実出力も、保存済みEscrow会計と同じ式であることを確認する。
            if (physical.isPresent()
                    && physical.orElseThrow()
                                    .state()
                            == CraftingTableBatchSnapshot.State
                                    .ACKNOWLEDGED
                    && !physical.orElseThrow()
                            .exactOutputs()
                            .equals(
                                    resolved.expectedOutputs())) {
                return StepAdvance.quarantined(
                        "NeoECO terminal receipt changed after output accounting");
            }
            boolean forgotten =
                    target.aco$forgetCraftingTableBatch(
                            receipt.transactionId(),
                            receipt.payloadDigest());
            boolean stillOwned =
                    target.aco$ownsCraftingTableBatch(
                            receipt.transactionId(),
                            receipt.payloadDigest());
            // forget成功または既に所有消失なら、外部物理仕事なしの終端状態へ進む。
            if (forgotten
                    || !stillOwned) {
                receipt.acknowledge();
                detail =
                        "";
                return StepAdvance.updated();
            }
            detail =
                    "waiting for NeoECO output acknowledgement";
            return StepAdvance.waiting();
        }

        return StepAdvance.waiting();
    }

    private static SchedulingLane schedulingLane(
            StepState state) {
        return switch (state) {
            /*
             * 外部Workerが所有する仕事と出力会計待ちは、次段を解禁できるため最優先する。
             */
            case ACCEPTED, OUTPUT_OBSERVED, OUTPUT_CREDITED ->
                    SchedulingLane.OWNED_THREAD;
            // 入力を既に予約した段は、Target選択または受理を先に完了させる。
            case INPUTS_RESERVED, TARGET_SELECTED ->
                    SchedulingLane.READY_SETUP;
            // 未予約段はEscrowに全依存入力が揃った場合だけ実行対象になる。
            case WAITING_FOR_INPUTS ->
                    SchedulingLane.DEPENDENCY_READY;
            // 通常実行では終端段へ再度触れない。
            case ACKNOWLEDGED, CANCELLED ->
                    SchedulingLane.TERMINAL;
        };
    }

    private TickOutcome returnNextResult(
            IGrid grid,
            IActionSource source) {
        Map<AEKey, BigInteger> outputs =
                expectedFinalOutputs();
        // 全成果物をMEへ返し、Escrowが空になった時だけ親Jobを完了させる。
        if (outputCursor >= outputs.size()) {
            // 予期しない中間素材が残る場合は、消したり追加挿入したりせず隔離する。
            if (!escrow.isEmpty()) {
                quarantine(
                        "unexpected inventory remains after returning final outputs");
                return TickOutcome.quarantined(
                        detail);
            }
            state =
                    State.COMPLETE;
            detail =
                    "";
            return TickOutcome.complete();
        }
        // Escrowに実在しない成果物を、計画値から直接生成してはならない。
        if (!escrow.snapshot()
                .equals(
                        outputs)) {
            quarantine(
                    "final output is absent from the physical crafting escrow");
            return TickOutcome.quarantined(
                    detail);
        }
        return prepareNetworkMutationBatch(
                grid,
                source,
                NetworkDirection.INSERT,
                MutationPurpose.FINAL_OUTPUT,
                outputs);
    }

    private TickOutcome advanceCancellation(
            Level level,
            Ae2CompiledCraftingGraphCache.Snapshot snapshot,
            int operationBudget) {
        int inspected =
                0;
        int changed =
                0;
        // 物理所有権のある段を解放し、未完成段の予約入力をEscrowへ戻す。
        for (StepReceipt receipt :
                steps) {
            // 終端段またはこのtickの予算を使い切った後は追加処理しない。
            if (receipt.state()
                            == StepState.ACKNOWLEDGED
                    || receipt.state()
                            == StepState.CANCELLED
                    || inspected >= operationBudget) {
                continue;
            }
            inspected++;
            lastConsumedOperations =
                    inspected;
            // 未受理段は外部所有者がいないため、予約入力を即座に戻せる。
            if (receipt.state()
                            == StepState.WAITING_FOR_INPUTS
                    || receipt.state()
                            == StepState.INPUTS_RESERVED
                    || receipt.state()
                            == StepState.TARGET_SELECTED) {
                restoreReservedInputsAndCancel(
                        receipt);
                changed++;
                continue;
            }
            ResolvedStep resolved =
                    resolveStep(
                            snapshot,
                            level,
                            receipt.index());
            BlockPos targetPosition =
                    BlockPos.of(
                            Objects.requireNonNull(
                                    receipt.targetPosition(),
                                    "cancellation target position"));
            // 未ロード中は外部Threadの所有権を推測せず、同じReceiptを保持する。
            if (!level.isLoaded(
                    targetPosition)) {
                detail =
                        "cancellation waits for the NeoECO target to load";
                continue;
            }
            BlockEntity targetEntity =
                    level.getBlockEntity(
                            targetPosition);
            /*
             * 対象型のBlock Entityがロード途中なら待つ。
             * 撤去または別設備への置換が確定した時だけ、保存済みEscrowへ戻す。
             */
            if (targetEntity == null
                    && level.getBlockState(
                                    targetPosition)
                            .hasBlockEntity()) {
                detail =
                        "cancellation waits for the target block entity to load";
                continue;
            }
            // 物理Target消失時は、代表仕事が外部へ実アイテムを出していない契約で復旧する。
            if (!(targetEntity
                    instanceof CraftingTableBatchTarget target)) {
                StepAdvance recovery =
                        recoverRemovedTargetForCancellation(
                                resolved,
                                receipt);
                // 保存出力不一致だけは、取消でも入力へ巻き戻さず隔離待ちにする。
                if (recovery.quarantined()) {
                    detail =
                            recovery.detail();
                    continue;
                }
                // 状態が変わった段だけ親NBTのdirty対象へ数える。
                if (recovery.changed()) {
                    changed++;
                }
                continue;
            }

            /*
             * 旧保存状態で実出力だけ観測済みなら、入力へ巻き戻さず出力を会計する。
             * 完成済みレシピを取消入力と同時に返すと複製になる。
             */
            if (receipt.state()
                    == StepState.OUTPUT_OBSERVED) {
                // 保存済み出力と現在式が違う場合は、取消でも数量を推測しない。
                if (!receipt.observedOutputs()
                        .equals(
                                resolved.expectedOutputs())) {
                    detail =
                            "cancellation waits because a saved output receipt changed";
                    continue;
                }
                escrow.credit(
                        receipt.observedOutputs());
                receipt.creditOutput();
                target.aco$acknowledgeCraftingTableBatch(
                        receipt.transactionId(),
                        receipt.payloadDigest());
                changed++;
                continue;
            }

            /*
             * 受理済みThreadが既に完成している場合は、実出力をEscrowへ入れてから
             * 全Escrow返却へ進む。RUNNINGだけが入力予約へ巻き戻せる。
             */
            if (receipt.state()
                    == StepState.ACCEPTED) {
                Optional<CraftingTableBatchSnapshot> physical =
                        target.aco$craftingTableBatchSnapshot(
                                receipt.transactionId(),
                                receipt.payloadDigest());
                // Snapshot待ちの所有中Threadは、二重取消せず次tickまで保持する。
                if (physical.isEmpty()
                        && target.aco$ownsCraftingTableBatch(
                                receipt.transactionId(),
                                receipt.payloadDigest())) {
                    detail =
                            "cancellation waits for the NeoECO Thread snapshot";
                    continue;
                }
                // 所有を失った未完成仕事は、予約入力だけを正確に戻す。
                if (physical.isEmpty()) {
                    restoreReservedInputsAndCancel(
                            receipt);
                    changed++;
                    continue;
                }
                CraftingTableBatchSnapshot physicalState =
                        physical.orElseThrow();
                // 完成済みまたは終端Receiptなら、実出力を一度だけ会計する。
                if (physicalState.state()
                                == CraftingTableBatchSnapshot.State
                                        .OUTPUT_READY
                        || physicalState.state()
                                == CraftingTableBatchSnapshot.State
                                        .ACKNOWLEDGED) {
                    // 実出力が現在式と一致しなければ、取消でも補正せず待機する。
                    if (!physicalState.exactOutputs()
                            .equals(
                                    resolved.expectedOutputs())) {
                        detail =
                                "cancellation waits because physical output differs from the recipe";
                        continue;
                    }
                    receipt.observeOutput(
                            physicalState.exactOutputs(),
                            physicalState.detail());
                    escrow.credit(
                            receipt.observedOutputs());
                    receipt.creditOutput();
                    // 生きたThreadなら、終端Receiptを残してから代表仕事を解放する。
                    if (physicalState.state()
                            == CraftingTableBatchSnapshot.State
                                    .OUTPUT_READY) {
                        target.aco$acknowledgeCraftingTableBatch(
                                receipt.transactionId(),
                                receipt.payloadDigest());
                    }
                    changed++;
                    continue;
                }
                // RUNNINGまたはTarget側取消済みだけを、通常の取消経路へ渡す。
                if (physicalState.state()
                                != CraftingTableBatchSnapshot.State
                                        .RUNNING
                        && physicalState.state()
                                != CraftingTableBatchSnapshot.State
                                        .CANCELLED) {
                    detail =
                            physicalState.detail();
                    continue;
                }
            }

            // 出力をEscrowへ反映済みの段は、入力を戻さず終端Receiptだけを解放する。
            if (receipt.state()
                    == StepState.OUTPUT_CREDITED) {
                Optional<CraftingTableBatchSnapshot> physical =
                        target.aco$craftingTableBatchSnapshot(
                                receipt.transactionId(),
                                receipt.payloadDigest());
                // 生きた完了Threadには、終端Receipt作成を冪等に再送する。
                if (physical.isPresent()
                        && physical.orElseThrow()
                                        .state()
                                == CraftingTableBatchSnapshot.State
                                        .OUTPUT_READY) {
                    target.aco$acknowledgeCraftingTableBatch(
                            receipt.transactionId(),
                            receipt.payloadDigest());
                }
                boolean forgotten =
                        target.aco$forgetCraftingTableBatch(
                                receipt.transactionId(),
                                receipt.payloadDigest());
                boolean stillOwned =
                        target.aco$ownsCraftingTableBatch(
                                receipt.transactionId(),
                                receipt.payloadDigest());
                // 既に解放済みの場合も、Escrow正本を維持して承認済みへ進める。
                if (forgotten
                        || !stillOwned) {
                    receipt.acknowledge();
                    changed++;
                }
                continue;
            }

            boolean cancelled =
                    target.aco$cancelCraftingTableBatch(
                            receipt.transactionId(),
                            receipt.payloadDigest());
            boolean stillOwned =
                    target.aco$ownsCraftingTableBatch(
                            receipt.transactionId(),
                            receipt.payloadDigest());
            // 取消成功または既に所有消失なら、未完成出力を捨てて予約入力だけを戻す。
            if (cancelled
                    || !stillOwned) {
                restoreReservedInputsAndCancel(
                        receipt);
                changed++;
            }
        }
        // 全物理所有権を解放した後からだけ、Escrow全量をMEへ返す。
        if (allRecipesTerminalForCancellation()) {
            state =
                    State.RETURNING_CANCELLED_ESCROW;
            detail =
                    "";
            return TickOutcome.changed();
        }
        return changed > 0
                ? TickOutcome.changed()
                : TickOutcome.waiting(
                        detail);
    }

    private TickOutcome returnNextCancelledStack(
            IGrid grid,
            IActionSource source) {
        Map<AEKey, BigInteger> remaining =
                escrow.snapshot();
        // 完成済み中間素材と未消費入力を全て返した時だけ取消完了にする。
        if (remaining.isEmpty()) {
            state =
                    State.CANCELLED;
            detail =
                    "";
            return TickOutcome.cancelled();
        }
        return prepareNetworkMutationBatch(
                grid,
                source,
                NetworkDirection.INSERT,
                MutationPurpose.CANCELLED_ESCROW,
                remaining);
    }

    private TickOutcome prepareNetworkMutationBatch(
            IGrid grid,
            IActionSource source,
            NetworkDirection direction,
            MutationPurpose purpose,
            Map<AEKey, BigInteger> amounts) {
        Map<AEKey, BigInteger> checked =
                checkedCounts(
                        amounts,
                        "pending exact storage amounts",
                        false);
        Optional<Map<AEKey, BigInteger>> exactAmounts =
                ExactVectorStorageService.exactStoredAmounts(
                        grid,
                        checked.keySet());
        // 正確なセル合計を取得できない場合は、long在庫へ縮退せず待機する。
        if (exactAmounts.isEmpty()) {
            detail =
                    "waiting for an exact BigInteger storage route";
            return TickOutcome.waiting(
                    detail);
        }
        Map<AEKey, BigInteger> before =
                exactAmounts.orElseThrow();
        // 抽出前の正確な在庫が不足する場合は、セルへ一切触れず待つ。
        if (direction == NetworkDirection.EXTRACT
                && !containsAll(
                        before,
                        checked)) {
            detail =
                    "exact BigInteger storage does not contain every reserved input";
            return TickOutcome.waiting(
                    detail);
        }
        boolean routeAvailable =
                direction == NetworkDirection.EXTRACT
                        ? ExactVectorStorageService.canExtractAll(
                                grid,
                                checked,
                                source)
                        : ExactVectorStorageService.canInsertAll(
                                grid,
                                checked,
                                source);
        // filter、優先度、容量を含む全量routeがない場合はpendingを作らない。
        if (!routeAvailable) {
            detail =
                    "exact BigInteger storage cannot accept the complete boundary mutation";
            return TickOutcome.waiting(
                    detail);
        }
        pendingNetworkMutation =
                new PendingNetworkBatchMutation(
                        UUID.randomUUID(),
                        direction,
                        purpose,
                        checked,
                        before);
        detail =
                "";
        return TickOutcome.changed();
    }

    private TickOutcome applyPendingNetworkMutation(
            IGrid grid,
            IActionSource source) {
        PendingNetworkBatchMutation pending =
                Objects.requireNonNull(
                        pendingNetworkMutation,
                        "pendingNetworkMutation");
        Optional<Map<AEKey, BigInteger>> exactAmounts =
                ExactVectorStorageService.exactStoredAmounts(
                        grid,
                        pending.amounts()
                                .keySet());
        // 対象セルが一時的に外れた場合は、同じpendingを保持して再接続を待つ。
        if (exactAmounts.isEmpty()) {
            detail =
                    "waiting to reconcile the exact storage mutation";
            return TickOutcome.waiting(
                    detail);
        }
        Map<AEKey, BigInteger> current =
                exactAmounts.orElseThrow();
        Map<AEKey, BigInteger> after =
                pending.afterAmounts();
        Optional<Map<AEKey, BigInteger>> remainingResult =
                ExactMutationReconciler.remainingAmounts(
                        pending.beforeAmounts(),
                        after,
                        current);
        // before/afterのどちらにも一致しないキーは、適用済みと推測せず隔離する。
        if (remainingResult.isEmpty()) {
            quarantine(
                    "exact storage changed while a crafting mutation was pending");
            return TickOutcome.quarantined(
                    detail);
        }
        Map<AEKey, BigInteger> remaining =
                remainingResult.orElseThrow();
        // 未適用キーがある場合だけ、その差分Batchを一度実行する。
        if (!remaining.isEmpty()) {
            ExactStorageMutationResult result =
                    pending.direction()
                                    == NetworkDirection.EXTRACT
                            ? ExactVectorStorageService.extractAll(
                                    grid,
                                    remaining,
                                    source)
                            : ExactVectorStorageService.insertAll(
                                    grid,
                                    remaining,
                                    source);
            // rollback不能と報告された操作は、再試行による複製を避けて隔離する。
            if (!result.successful()) {
                // 成否不明ならpendingを残したまま親Transactionを隔離する。
                if (result.stateUncertain()) {
                    quarantine(
                            result.detail());
                    return TickOutcome.quarantined(
                            detail);
                }
                detail =
                        result.detail();
                return TickOutcome.waiting(
                        detail);
            }
            Optional<Map<AEKey, BigInteger>> verifiedAmounts =
                    ExactVectorStorageService.exactStoredAmounts(
                            grid,
                            pending.amounts()
                                    .keySet());
            // 成功応答後にセルが外れた場合は、pendingを保持して次tickに再照合する。
            if (verifiedAmounts.isEmpty()) {
                detail =
                        "waiting to verify the completed exact storage mutation";
                return TickOutcome.waiting(
                        detail);
            }
            // 実在庫が全キーのafterと一致するまで、親Escrow会計を進めない。
            if (!verifiedAmounts.orElseThrow()
                    .equals(
                            after)) {
                quarantine(
                        "exact storage did not match the committed crafting mutation");
                return TickOutcome.quarantined(
                        detail);
            }
        }

        /*
         * remainingが空なら全キーが停止前に適用済み、非空なら今回の再試行で適用済み。
         * どちらも親Escrow側の会計だけを一度進める。
         */
        if (!remaining.isEmpty()
                || current.equals(
                        after)) {
            finalizePendingNetworkMutation(
                    pending);
            pendingNetworkMutation =
                    null;
            detail =
                    "";
            return TickOutcome.changed();
        }
        /*
         * Reconcilerが成功してremainingも空ならcurrent==afterになるため到達しない。
         * 将来の変更で契約が崩れた場合も、推測で完了させず隔離する。
         */
        if (!current.equals(
                after)) {
            quarantine(
                    "exact storage reconciliation produced an inconsistent result");
            return TickOutcome.quarantined(
                    detail);
        }
        throw new IllegalStateException(
                "unreachable exact storage reconciliation state");
    }

    private void finalizePendingNetworkMutation(
            PendingNetworkBatchMutation pending) {
        Map<AEKey, BigInteger> amounts =
                pending.amounts();
        switch (pending.purpose()) {
            case BOUNDARY_INPUT -> {
                Map<AEKey, BigInteger> expected =
                        countsFromStacks(
                                plan.totalInputs());
                // Cursorとpendingの全キー・数量が一致する場合だけEscrowへ所有権を移す。
                if (state
                                != State.RESERVING_BOUNDARY_INPUTS
                        || inputCursor != 0
                        || !expected.equals(
                                amounts)) {
                    throw new IllegalStateException(
                            "pending boundary inputs do not match the plan");
                }
                escrow.credit(
                        amounts);
                inputCursor =
                        plan.totalInputs()
                                .size();
            }
            case FINAL_OUTPUT -> {
                Map<AEKey, BigInteger> expected =
                        expectedFinalOutputs();
                // 計画値ではなくEscrow実在量から、挿入済み全成果物だけを引く。
                if (state
                                != State.RETURNING_RESULTS
                        || outputCursor != 0
                        || !expected.equals(
                                amounts)) {
                    throw new IllegalStateException(
                            "pending final outputs do not match the plan");
                }
                escrow.debitExact(
                        amounts);
                outputCursor =
                        expected.size();
            }
            case CANCELLED_ESCROW -> {
                // 取消返却は現在Escrowに残る全キー・数量だけを一括減算する。
                if (state
                                != State.RETURNING_CANCELLED_ESCROW
                        || !escrow.snapshot()
                                .equals(
                                        amounts)) {
                    throw new IllegalStateException(
                        "pending cancellation output is in another state");
                }
                escrow.debitExact(
                        amounts);
            }
        }
    }

    private void restoreReservedInputsAndCancel(
            StepReceipt receipt) {
        Map<AEKey, BigInteger> reserved =
                receipt.reservedInputs();
        // 入力を予約していた段だけ、その全量をEscrowへ戻す。
        if (!reserved.isEmpty()) {
            escrow.credit(
                    reserved);
        }
        receipt.cancel();
    }

    private boolean validateCompiledTree(
            Ae2CompiledCraftingGraphCache.Snapshot snapshot,
            Level level) {
        try {
            List<ResolvedStep> resolvedSteps =
                    new ArrayList<>(
                            steps.size());
            // 現在世代の各Pattern式を一度だけ解決し、検証成功後のtickで再利用する。
            for (int index = 0;
                    index < steps.size();
                    index++) {
                resolvedSteps.add(
                        resolveStepFresh(
                                snapshot,
                                level,
                                index));
            }
            ExactCraftingEscrow<AEKey> virtual =
                    new ExactCraftingEscrow<>();
            Map<AEKey, BigInteger> boundary =
                    new LinkedHashMap<>();
            // MEから予約する正味境界素材を、仮想Escrowへ一度だけ登録する。
            for (ExactStack input :
                    plan.totalInputs()) {
                boundary.merge(
                        input.key(),
                        input.amount(),
                        BigInteger::add);
            }
            // 空の境界も許し、存在する場合だけ正数MapをEscrowへ加える。
            if (!boundary.isEmpty()) {
                virtual.credit(
                        boundary);
            }
            /*
             * 材料側から完成品側へ、各固有Patternを一回だけ会計する。
             * 実行数量はBigInteger係数であり、このloop回数には使わない。
             */
            for (int index = 0;
                    index < steps.size();
                    index++) {
                ResolvedStep resolved =
                        resolvedSteps.get(
                                index);
                // 依存出力が揃わない順序の計画は、実設備へ渡す前に拒否する。
                if (!virtual.containsAll(
                        resolved.inputTotals())) {
                    return false;
                }
                virtual.debitExact(
                        resolved.inputTotals());
                virtual.credit(
                        resolved.expectedOutputs());
            }
            boolean valid =
                    virtual.snapshot()
                            .equals(
                                    expectedFinalOutputs());
            /*
             * 全段の保存式と最終収支が一致した時だけ、新世代のcacheへ原子的に交換する。
             * 失敗時は進行中Transactionの取消・会計に必要な旧証明を保持する。
             */
            if (valid) {
                resolvedStepCache =
                        List.copyOf(
                                resolvedSteps);
            }
            return valid;
        } catch (RuntimeException | LinkageError invalid) {
            return false;
        }
    }

    private boolean isValidatedFor(
            Ae2CompiledCraftingGraphCache.Snapshot snapshot) {
        return resolvedStepCache.size()
                        == steps.size()
                && validatedPatternGeneration
                        == snapshot.graph()
                                .generation()
                && validatedRecipeGeneration
                        == snapshot.recipeGeneration();
    }

    private ResolvedStep resolveStep(
            Ae2CompiledCraftingGraphCache.Snapshot snapshot,
            Level level,
            int index) {
        /*
         * 一度証明した式は、再検証失敗後の取消・AE2会計でもTransactionの正本として使う。
         * 新しい仕事を配送する前の世代一致はadvancePhysicalRecipes側で別途検査する。
         */
        if (resolvedStepCache.size()
                == steps.size()) {
            return resolvedStepCache.get(
                    index);
        }
        return resolveStepFresh(
                snapshot,
                level,
                index);
    }

    private ResolvedStep resolveStepFresh(
            Ae2CompiledCraftingGraphCache.Snapshot snapshot,
            Level level,
            int index) {
        ExactCraftingStep step =
                plan.craftingSteps()
                        .get(
                                index);
        IPatternDetails pattern =
                snapshot.pattern(
                        step.patternId());
        ExactPatternFormula formula =
                pattern == null
                        ? null
                        : ExactPatternFormula.tryCreate(
                                        pattern,
                                        level,
                                        step.selectedInputs())
                                .orElse(
                                        null);
        // Pattern消失、加工Pattern化、保存済み候補の不一致を推測で補わない。
        if (formula == null) {
            throw new IllegalStateException(
                    "saved crafting-table pattern is no longer deterministic");
        }
        return new ResolvedStep(
                step,
                pattern,
                formula,
                formula.exactInputTotals(
                        step.executions()),
                formula.exactExpectedOutputTotals(
                        step.executions()));
    }

    /**
     * 形成設備が撤去・置換された実行段を、ACOの正本Escrowから復旧する。
     *
     * <p>BigInteger物理Threadは代表一回分しか持たず、正確な入力はACOが所有する。
     * 完成前なら同じTransaction IDで別Workerへ再配送し、実出力観測後なら保存済みの
     * exact Receiptだけを一度会計する。</p>
     */
    private StepAdvance recoverRemovedTarget(
            ResolvedStep resolved,
            StepReceipt receipt) {
        return switch (receipt.state()) {
            case TARGET_SELECTED, ACCEPTED -> {
                receipt.retryAnotherTarget();
                detail =
                        "physical target was removed; retrying on another NeoECO worker";
                yield StepAdvance.updated();
            }
            case OUTPUT_OBSERVED -> {
                // 観測済み実出力が現在の式と違う場合は、設備消失後も数量を推測しない。
                if (!receipt.observedOutputs()
                        .equals(
                                resolved.expectedOutputs())) {
                    yield StepAdvance.quarantined(
                            "saved physical output differs after target removal");
                }
                escrow.credit(
                        receipt.observedOutputs());
                receipt.creditOutput();
                receipt.acknowledge();
                detail =
                        "";
                yield StepAdvance.updated();
            }
            case OUTPUT_CREDITED -> {
                /*
                 * exact出力は既にEscrowへ入っており、消失した設備側に実在出力はない。
                 * 終端Receiptのforgetだけを完了済みとして扱う。
                 */
                receipt.acknowledge();
                detail =
                        "";
                yield StepAdvance.updated();
            }
            default ->
                    StepAdvance.quarantined(
                            "removed physical target has an incompatible recipe state");
        };
    }

    /** 取消中にTargetが消失した段を、完成前入力または完成後出力へ一意に分類する。 */
    private StepAdvance recoverRemovedTargetForCancellation(
            ResolvedStep resolved,
            StepReceipt receipt) {
        return switch (receipt.state()) {
            case ACCEPTED -> {
                restoreReservedInputsAndCancel(
                        receipt);
                yield StepAdvance.updated();
            }
            case OUTPUT_OBSERVED -> {
                // 完成済みReceiptは入力へ巻き戻さず、実出力としてEscrowへ返す。
                if (!receipt.observedOutputs()
                        .equals(
                                resolved.expectedOutputs())) {
                    yield StepAdvance.quarantined(
                            "saved physical output differs during target-removal cancellation");
                }
                escrow.credit(
                        receipt.observedOutputs());
                receipt.creditOutput();
                receipt.acknowledge();
                yield StepAdvance.updated();
            }
            case OUTPUT_CREDITED -> {
                // Escrow会計済みなら、失われた終端Receiptの外部所有権だけを閉じる。
                receipt.acknowledge();
                yield StepAdvance.updated();
            }
            default ->
                    StepAdvance.quarantined(
                            "removed cancellation target has an incompatible recipe state");
        };
    }

    private CraftingTableBatchRequest request(
            ResolvedStep resolved,
            StepReceipt receipt) {
        // 予約時に保存した入力が現在式と違えば、外部Threadへ所有権を渡さない。
        if (!receipt.reservedInputs()
                .equals(
                        resolved.inputTotals())) {
            throw new IllegalStateException(
                    "reserved crafting inputs no longer match the recipe formula");
        }
        return new CraftingTableBatchRequest(
                receipt.transactionId(),
                plan.transactionId(),
                plan.parentJobId(),
                receipt.payloadDigest(),
                receipt.index(),
                CraftingTableBatchMode.BIG_INTEGER_JOB,
                resolved.pattern(),
                resolved.step()
                        .executions(),
                resolved.formula()
                        .copyInputsPerExecution(),
                resolved.formula()
                        .exactSlotInputs(
                                resolved.step()
                                        .executions()),
                resolved.formula()
                        .outputsPerExecution(),
                resolved.formula()
                        .remainingPerExecution(),
                resolved.expectedOutputs());
    }

    private static SelectedTarget selectTarget(
            IGrid grid,
            IPatternDetails pattern,
            int routeCursor,
            UUID transactionId,
            String payloadDigest) {
        // ACOの世代付きProviderキャッシュを持つ実CraftingServiceだけを対象にする。
        if (!(grid.getCraftingService()
                instanceof CraftingService service)) {
            return null;
        }
        Map<Long, BlockEntity> targets =
                new LinkedHashMap<>();
        // 同じPattern Busが重複Providerとして見えても位置ごとに一件へ畳み込む。
        for (ICraftingProvider provider :
                PatternProviderRoutingCache.candidates(
                        service,
                        pattern)) {
            // Pattern所有Providerではない候補を、物理Targetとして推測しない。
            if (!(provider
                    instanceof ProviderOwnedPatternBatchTarget owned)) {
                continue;
            }
            BlockEntity target =
                    owned.aco$getProviderOwnedBatchTarget();
            // 永続化できるBlock EntityのTarget契約だけを候補にする。
            if (target
                    instanceof CraftingTableBatchTarget) {
                targets.putIfAbsent(
                        target.getBlockPos()
                                .asLong(),
                        target);
            }
        }
        // 対応設備がない場合は、呼出側で入力予約を維持して待つ。
        if (targets.isEmpty()) {
            return null;
        }
        List<BlockEntity> ordered =
                List.copyOf(
                        targets.values());
        /*
         * 親保存前にTargetだけが受理済みとなった停止復旧では、
         * 既存所有者を通常のラウンドロビン候補より優先する。
         */
        for (int index = 0;
                index < ordered.size();
                index++) {
            BlockEntity candidate =
                    ordered.get(
                            index);
            // Target契約はtargets作成時に検査済みなので、安全に所有権を照会できる。
            if (((CraftingTableBatchTarget) candidate)
                    .aco$ownsCraftingTableBatch(
                            transactionId,
                            payloadDigest)) {
                return new SelectedTarget(
                        candidate.getBlockPos()
                                .asLong(),
                        Math.floorMod(
                                index + 1,
                                ordered.size()));
            }
        }
        int selectedIndex =
                Math.floorMod(
                        routeCursor,
                        ordered.size());
        BlockEntity selected =
                ordered.get(
                        selectedIndex);
        return new SelectedTarget(
                selected.getBlockPos()
                        .asLong(),
                Math.floorMod(
                        selectedIndex
                                + 1,
                        ordered.size()));
    }

    private Map<AEKey, BigInteger> expectedFinalOutputs() {
        Map<AEKey, BigInteger> result =
                new LinkedHashMap<>();
        // 要求成果物を先に置き、余剰と返却物を同一キーへ正確に合算する。
        for (ExactStack output :
                plan.finalOutputs()) {
            mergePositive(
                    result,
                    output.key(),
                    output.amount());
        }
        // 丸め余剰や容器返却物も、実物理ツリーの最終Escrowへ残る。
        for (ExactStack output :
                plan.remainingOutputs()) {
            mergePositive(
                    result,
                    output.key(),
                    output.amount());
        }
        return immutableOrderedMap(
                result);
    }

    private boolean allRecipesAcknowledged() {
        // 全段がOUTPUT_CREDITEDを経由してACKNOWLEDGEDになった場合だけ成功扱いする。
        for (StepReceipt receipt :
                steps) {
            // 一段でも未承認なら、最終出力をMEへ返さない。
            if (receipt.state()
                    != StepState.ACKNOWLEDGED) {
                return false;
            }
        }
        return true;
    }

    private boolean allRecipesTerminalForCancellation() {
        // ACKNOWLEDGEDまたはCANCELLEDだけが、外部物理所有権のない終端状態。
        for (StepReceipt receipt :
                steps) {
            // 一段でも実Threadを所有し得る状態なら、Escrow返却へ進まない。
            if (receipt.state()
                            != StepState.ACKNOWLEDGED
                    && receipt.state()
                            != StepState.CANCELLED) {
                return false;
            }
        }
        return true;
    }

    private void quarantine(
            String reason) {
        state =
                State.QUARANTINED;
        detail =
                checkedDetail(
                        reason);
    }

    private void validateState() {
        int outputCount =
                expectedFinalOutputs()
                        .size();
        int inputCount =
                plan.totalInputs()
                        .size();
        /*
         * 境界操作は全キー一括なので、Cursorは未適用0または全適用件数だけを許す。
         * 中間値を復元すると一部キーの所有権を親とMEの両方が持ち得る。
         */
        if ((inputCursor != 0
                        && inputCursor != inputCount)
                || (outputCursor != 0
                        && outputCursor != outputCount)
                || schedulerCursor < 0
                || schedulerCursor >= steps.size()
                || validatedPatternGeneration < -1L
                || validatedRecipeGeneration < -1L) {
            throw new IllegalArgumentException(
                    "invalid physical crafting-tree cursor");
        }
        // 実レシピ開始後は、全境界入力がEscrowへ確定済みでなければならない。
        if ((state == State.EXECUTING_RECIPES
                        || state == State.RETURNING_RESULTS
                        || state == State.COMPLETE)
                && inputCursor != inputCount) {
            throw new IllegalArgumentException(
                    "physical crafting state has incomplete boundary inputs");
        }
        // 完了状態は全最終出力をMEへ返した後だけ復元できる。
        if (state == State.COMPLETE
                && outputCursor != outputCount) {
            throw new IllegalArgumentException(
                    "complete physical crafting state has pending outputs");
        }
        // 最終返却前の状態が出力済みCursorを持つ場合は、二重挿入を避けて拒否する。
        if (state != State.RETURNING_RESULTS
                && state != State.COMPLETE
                && state != State.QUARANTINED
                && outputCursor != 0) {
            throw new IllegalArgumentException(
                    "physical crafting state has an early output cursor");
        }
        // 最終出力返却状態は、全物理段の承認後だけ有効にする。
        if ((state == State.RETURNING_RESULTS
                        || state == State.COMPLETE)
                && !allRecipesAcknowledged()) {
            throw new IllegalArgumentException(
                    "final output state contains unfinished physical recipes");
        }
        // 完了状態でEscrowが残る場合、未返却出力を失うためロードを拒否する。
        if ((state == State.COMPLETE
                        || state == State.CANCELLED)
                && !escrow.isEmpty()) {
            throw new IllegalArgumentException(
                    "terminal physical crafting-tree state retains inventory");
        }
        // pending境界操作は、用途に対応する親状態でだけ復元できる。
        if (pendingNetworkMutation != null
                && !pendingNetworkMutation.validFor(
                        state)) {
            throw new IllegalArgumentException(
                    "pending exact storage mutation is in an incompatible state");
        }
        /*
         * 保留取消は、まだ成功・取消・隔離が確定していない実行前半だけに存在できる。
         * 終端側に残る値は破損NBTとして拒否する。
         */
        if (cancellationRequested
                && (state == State.RETURNING_RESULTS
                        || state == State.CANCELLING_THREADS
                        || state == State.RETURNING_CANCELLED_ESCROW
                        || state == State.COMPLETE
                        || state == State.CANCELLED
                        || state == State.QUARANTINED)) {
            throw new IllegalArgumentException(
                    "pending cancellation is in an incompatible state");
        }
    }

    private static List<StepReceipt> checkedReceipts(
            PreparedVectorBatch plan,
            List<StepReceipt> source) {
        List<StepReceipt> copy =
                List.copyOf(
                        Objects.requireNonNull(
                                source,
                                "steps"));
        // 固有Pattern一件につきReceipt一件を必須にする。
        if (copy.size()
                != plan.craftingSteps()
                        .size()) {
            throw new IllegalArgumentException(
                    "physical recipe receipts do not match the plan");
        }
        Map<UUID, Boolean> ids =
                new LinkedHashMap<>();
        // 保存順、index、UUID重複を一度だけ検査する。
        for (int index = 0;
                index < copy.size();
                index++) {
            StepReceipt receipt =
                    Objects.requireNonNull(
                            copy.get(
                                    index),
                            "step receipt");
            // indexずれまたはTransaction ID重複は、別段の所有権混同になるため拒否する。
            if (receipt.index()
                            != index
                    || ids.put(
                                    receipt.transactionId(),
                                    Boolean.TRUE)
                            != null) {
                throw new IllegalArgumentException(
                        "physical recipe receipts are duplicated or out of order");
            }
        }
        return copy;
    }

    private static <K> void mergePositive(
            Map<K, BigInteger> target,
            K key,
            BigInteger amount) {
        Objects.requireNonNull(
                key,
                "key");
        BigInteger checked =
                Objects.requireNonNull(
                        amount,
                        "amount");
        // 0以下を在庫へ入れず、同一キーはBigInteger加算で一行に畳み込む。
        if (checked.signum() <= 0) {
            throw new IllegalArgumentException(
                    "exact crafting amount must be positive");
        }
        target.merge(
                key,
                checked,
                BigInteger::add);
    }

    private static <K> Map<K, BigInteger> checkedCounts(
            Map<K, BigInteger> source,
            String name,
            boolean allowEmpty) {
        Objects.requireNonNull(
                source,
                name);
        // キー件数上限は数量ではなく、破損NBTによる巨大Map確保だけを防ぐ。
        if (source.size()
                > MAXIMUM_EXACT_KEYS) {
            throw new IllegalArgumentException(
                    name + " has too many keys");
        }
        Map<K, BigInteger> result =
                new LinkedHashMap<>();
        // 各キーを正数BigIntegerとして順序付き不変Mapへ複製する。
        for (Map.Entry<K, BigInteger> entry :
                source.entrySet()) {
            K key =
                    Objects.requireNonNull(
                            entry.getKey(),
                            name + " key");
            BigInteger amount =
                    Objects.requireNonNull(
                            entry.getValue(),
                            name + " amount");
            // 0以下のentryは存在しないキーと区別できないため拒否する。
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException(
                        name + " contains a non-positive amount");
            }
            result.put(
                    key,
                    amount);
        }
        // 呼出側が空を許可しない台帳には一件以上を要求する。
        if (!allowEmpty
                && result.isEmpty()) {
            throw new IllegalArgumentException(
                    name + " must not be empty");
        }
        return immutableOrderedMap(
                result);
    }

    private static <K> Map<K, BigInteger> immutableOrderedMap(
            Map<K, BigInteger> source) {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(
                        source));
    }

    private static ListTag encodeCounts(
            Map<AEKey, BigInteger> counts) {
        List<ExactStack> stacks =
                new ArrayList<>(
                        counts.size());
        // Map順を保った一キー一要素のExactStack列へ変換する。
        for (Map.Entry<AEKey, BigInteger> entry :
                counts.entrySet()) {
            stacks.add(
                    new ExactStack(
                            entry.getKey(),
                            entry.getValue()));
        }
        return PreparedVectorBatchCodec.encodeStacks(
                stacks);
    }

    private static ListTag encodeNonNegativeCounts(
            Map<AEKey, BigInteger> counts) {
        ListTag result =
                new ListTag();
        // 0在庫も停止前の正確なbefore値なので、一キー一要素で明示保存する。
        for (Map.Entry<AEKey, BigInteger> entry :
                counts.entrySet()) {
            CompoundTag encoded =
                    new CompoundTag();
            encoded.put(
                    "key",
                    entry.getKey()
                            .toTagGeneric(com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require()));
            PreparedVectorBatchCodec.putNonNegative(
                    encoded,
                    "amount",
                    entry.getValue());
            result.add(
                    encoded);
        }
        return result;
    }

    private static Map<AEKey, BigInteger> decodeCounts(
            CompoundTag owner,
            String name) {
        List<ExactStack> stacks =
                PreparedVectorBatchCodec.decodeStacks(
                        owner,
                        name);
        Map<AEKey, BigInteger> result =
                new LinkedHashMap<>();
        // 同じキーの二重保存を数量合算せず、破損Receiptとして拒否する。
        for (ExactStack stack :
                stacks) {
            // 一キー一要素の保存契約を破るNBTは復元しない。
            if (result.putIfAbsent(
                            stack.key(),
                            stack.amount())
                    != null) {
                throw new IllegalArgumentException(
                        name + " contains duplicate keys");
            }
        }
        return immutableOrderedMap(
                result);
    }

    private static Map<AEKey, BigInteger> decodeNonNegativeCounts(
            CompoundTag owner,
            String name) {
        Tag raw =
                owner.get(
                        name);
        // 0在庫を含むbefore一覧はCompound Listだけを受理し、件数上限を適用する。
        if (!(raw
                        instanceof ListTag list)
                || (!list.isEmpty()
                        && list.getElementType()
                                != Tag.TAG_COMPOUND)
                || list.isEmpty()
                || list.size()
                        > MAXIMUM_EXACT_KEYS) {
            throw new IllegalArgumentException(
                    "invalid exact non-negative count list "
                            + name);
        }
        Map<AEKey, BigInteger> result =
                new LinkedHashMap<>();
        // 全キーを一度だけ復元し、0未満・重複・不明キーを拒否する。
        for (int index = 0;
                index < list.size();
                index++) {
            CompoundTag encoded =
                    list.getCompound(
                            index);
            AEKey key =
                    AEKey.fromTagGeneric(com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require(),
                            encoded.getCompound(
                                    "key"));
            BigInteger amount =
                    PreparedVectorBatchCodec.readNonNegative(
                            encoded,
                            "amount");
            // key欠落または二重保存は、before/after照合を一意にできない。
            if (key == null
                    || result.putIfAbsent(
                                    key,
                                    amount)
                            != null) {
                throw new IllegalArgumentException(
                        "invalid exact non-negative count entry");
            }
        }
        return immutableOrderedMap(
                result);
    }

    private static Map<AEKey, BigInteger> countsFromStacks(
            List<ExactStack> stacks) {
        Map<AEKey, BigInteger> result =
                new LinkedHashMap<>();
        // PreparedVectorBatchが一意性を検査済みでも、境界で再度重複を拒否する。
        for (ExactStack stack :
                stacks) {
            // 同一キーを暗黙に合算すると保存Cursorと計画の不一致を隠すため拒否する。
            if (result.putIfAbsent(
                            stack.key(),
                            stack.amount())
                    != null) {
                throw new IllegalArgumentException(
                        "exact stack list contains duplicate keys");
            }
        }
        return immutableOrderedMap(
                result);
    }

    private static <K> boolean containsAll(
            Map<K, BigInteger> available,
            Map<K, BigInteger> required) {
        // 全要求キーが正確な在庫量以下の場合だけ、Batch抽出を許可する。
        for (Map.Entry<K, BigInteger> entry :
                required.entrySet()) {
            // 一件でも不足すればセルへ触る前にfalseを返す。
            if (available
                            .getOrDefault(
                                    entry.getKey(),
                                    BigInteger.ZERO)
                            .compareTo(
                                    entry.getValue())
                    < 0) {
                return false;
            }
        }
        return true;
    }

    private static ListTag requireCompoundList(
            CompoundTag owner,
            String name,
            int expectedEntries) {
        Tag raw =
                owner.get(
                        name);
        // Compound以外の要素と計画件数の不一致を、走査前に拒否する。
        if (!(raw
                        instanceof ListTag list)
                || (!list.isEmpty()
                        && list.getElementType()
                                != Tag.TAG_COMPOUND)
                || list.size()
                        != expectedEntries) {
            throw new IllegalArgumentException(
                    "invalid physical crafting-tree list "
                            + name);
        }
        return list;
    }

    private static String stepDigest(
            PreparedVectorBatch plan,
            ExactCraftingStep step,
            int index,
            UUID transactionId) {
        StringBuilder source =
                new StringBuilder()
                        .append(
                                plan.programFingerprint())
                        .append('|')
                        .append(
                                step.patternId())
                        .append('|')
                        .append(
                                step.depth())
                        .append('|')
                        .append(
                                step.executions());
        // 選択slot順、具体AEKey、一回入力量をTransaction識別子へ含める。
        for (var input :
                step.selectedInputs()) {
            source.append(
                            "|slot:")
                    .append(
                            input.key()
                                    .toTagGeneric(com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require()))
                    .append('@')
                    .append(
                            input.amountPerExecution());
        }
        source.append('|')
                .append(
                        index)
                .append('|')
                .append(
                        transactionId);
        return StableFingerprint.sha256(
                source.toString());
    }

    private static State parseState(
            String name) {
        try {
            return State.valueOf(
                    name);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "invalid physical crafting-tree state",
                    invalid);
        }
    }

    private static StepState parseStepState(
            String name) {
        try {
            return StepState.valueOf(
                    name);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "invalid physical crafting recipe state",
                    invalid);
        }
    }

    private static String checkedDetail(
            String value) {
        String checked =
                Objects.requireNonNull(
                        value,
                        "detail");
        // 異常に長い例外文字列を親CPU NBTへ保持しない。
        if (checked.length()
                > MAXIMUM_DETAIL_LENGTH) {
            return checked.substring(
                    0,
                    MAXIMUM_DETAIL_LENGTH);
        }
        return checked;
    }

    public enum State {
        VALIDATING,
        RESERVING_BOUNDARY_INPUTS,
        EXECUTING_RECIPES,
        RETURNING_RESULTS,
        CANCELLING_THREADS,
        RETURNING_CANCELLED_ESCROW,
        COMPLETE,
        CANCELLED,
        QUARANTINED
    }

    private enum StepState {
        WAITING_FOR_INPUTS,
        INPUTS_RESERVED,
        TARGET_SELECTED,
        ACCEPTED,
        OUTPUT_OBSERVED,
        OUTPUT_CREDITED,
        ACKNOWLEDGED,
        CANCELLED
    }

    private enum SchedulingLane {
        OWNED_THREAD,
        READY_SETUP,
        DEPENDENCY_READY,
        TERMINAL;

        /** 終端Laneを除いた、固定の優先実行順。 */
        private static final List<SchedulingLane> RUNNABLE_ORDER =
                List.of(
                        OWNED_THREAD,
                        READY_SETUP,
                        DEPENDENCY_READY);
    }

    private enum NetworkDirection {
        INSERT,
        EXTRACT
    }

    private enum MutationPurpose {
        BOUNDARY_INPUT,
        FINAL_OUTPUT,
        CANCELLED_ESCROW
    }

    private static final class StepReceipt {
        private final int index;
        private final UUID transactionId;
        private final String payloadDigest;
        private StepState state;
        /** Pattern Busが一度でも実仕事を所有したことを、取消後も失わない累積フラグ。 */
        private boolean dispatched;
        private Long targetPosition;
        private int routeCursor;
        private int progress;
        private int maximumProgress;
        private Map<AEKey, BigInteger> reservedInputs;
        private Map<AEKey, BigInteger> observedOutputs;
        private String detail;

        private StepReceipt(
                int index,
                UUID transactionId,
                String payloadDigest,
                StepState state,
                boolean dispatched,
                Long targetPosition,
                int routeCursor,
                int progress,
                int maximumProgress,
                Map<AEKey, BigInteger> reservedInputs,
                Map<AEKey, BigInteger> observedOutputs,
                String detail) {
            this.index =
                    index;
            this.transactionId =
                    Objects.requireNonNull(
                            transactionId,
                            "transactionId");
            this.payloadDigest =
                    Objects.requireNonNull(
                                    payloadDigest,
                                    "payloadDigest")
                            .trim();
            this.state =
                    Objects.requireNonNull(
                            state,
                            "state");
            this.dispatched =
                    dispatched;
            this.targetPosition =
                    targetPosition;
            this.routeCursor =
                    routeCursor;
            this.progress =
                    progress;
            this.maximumProgress =
                    maximumProgress;
            this.reservedInputs =
                    checkedCounts(
                            reservedInputs,
                            "reservedInputs",
                            true);
            this.observedOutputs =
                    checkedCounts(
                            observedOutputs,
                            "observedOutputs",
                            true);
            this.detail =
                    checkedDetail(
                            detail);
            validate();
        }

        private static StepReceipt waiting(
                int index,
                UUID transactionId,
                String payloadDigest) {
            return new StepReceipt(
                    index,
                    transactionId,
                    payloadDigest,
                    StepState.WAITING_FOR_INPUTS,
                    false,
                    null,
                    0,
                    0,
                    1,
                    Map.of(),
                    Map.of(),
                    "");
        }

        private static StepReceipt load(
                CompoundTag owner) {
            // Transaction識別子がない段は、外部所有権を再照合できない。
            if (!owner.hasUUID(
                    "transactionId")) {
                throw new IllegalArgumentException(
                        "physical recipe receipt has no transaction id");
            }
            Long targetPosition =
                    owner.contains(
                                    "targetPosition",
                                    Tag.TAG_LONG)
                            ? owner.getLong(
                                    "targetPosition")
                            : null;
            StepState state = parseStepState(
                    owner.getString(
                            "state"));
            /*
             * 旧schemaには累積フラグがない。外部所有以降の状態だけを配送済みと復元し、
             * CANCELLEDの曖昧な旧Receiptは新しい実Job会計へ使わない。
             */
            boolean dispatched = owner.contains(
                            "dispatched",
                            Tag.TAG_BYTE)
                    ? owner.getBoolean("dispatched")
                    : state == StepState.ACCEPTED
                            || state == StepState.OUTPUT_OBSERVED
                            || state == StepState.OUTPUT_CREDITED
                            || state == StepState.ACKNOWLEDGED;
            return new StepReceipt(
                    owner.getInt(
                            "index"),
                    owner.getUUID(
                            "transactionId"),
                    owner.getString(
                            "payloadDigest"),
                    state,
                    dispatched,
                    targetPosition,
                    owner.getInt(
                            "routeCursor"),
                    owner.getInt(
                            "progress"),
                    owner.getInt(
                            "maximumProgress"),
                    decodeCounts(
                            owner,
                            "reservedInputs"),
                    decodeCounts(
                            owner,
                            "observedOutputs"),
                    owner.getString(
                            "detail"));
        }

        private CompoundTag save() {
            CompoundTag owner =
                    new CompoundTag();
            owner.putInt(
                    "index",
                    index);
            owner.putUUID(
                    "transactionId",
                    transactionId);
            owner.putString(
                    "payloadDigest",
                    payloadDigest);
            owner.putString(
                    "state",
                    state.name());
            if (dispatched) {
                owner.putBoolean(
                        "dispatched",
                        true);
            }
            owner.putInt(
                    "routeCursor",
                    routeCursor);
            owner.putInt(
                    "progress",
                    progress);
            owner.putInt(
                    "maximumProgress",
                    maximumProgress);
            owner.put(
                    "reservedInputs",
                    encodeCounts(
                            reservedInputs));
            owner.put(
                    "observedOutputs",
                    encodeCounts(
                            observedOutputs));
            owner.putString(
                    "detail",
                    detail);
            // Target選択後からThread承認前までだけ、BlockPosを保存する。
            if (targetPosition != null) {
                owner.putLong(
                        "targetPosition",
                        targetPosition);
            }
            return owner;
        }

        private int index() {
            return index;
        }

        private UUID transactionId() {
            return transactionId;
        }

        private String payloadDigest() {
            return payloadDigest;
        }

        private StepState state() {
            return state;
        }

        private boolean dispatched() {
            return dispatched;
        }

        private boolean outputCredited() {
            return state == StepState.OUTPUT_CREDITED
                    || state == StepState.ACKNOWLEDGED;
        }

        private Long targetPosition() {
            return targetPosition;
        }

        private int routeCursor() {
            return routeCursor;
        }

        private Map<AEKey, BigInteger> reservedInputs() {
            return reservedInputs;
        }

        private Map<AEKey, BigInteger> observedOutputs() {
            return observedOutputs;
        }

        private void reserveInputs(
                Map<AEKey, BigInteger> inputs) {
            reservedInputs =
                    checkedCounts(
                            inputs,
                            "reservedInputs",
                            false);
            state =
                    StepState.INPUTS_RESERVED;
            detail =
                    "";
        }

        private void selectTarget(
                long position,
                int nextRouteCursor) {
            targetPosition =
                    position;
            routeCursor =
                    Math.max(
                            0,
                            nextRouteCursor);
            state =
                    StepState.TARGET_SELECTED;
            detail =
                    "";
        }

        private void accept() {
            state =
                    StepState.ACCEPTED;
            dispatched =
                    true;
            progress =
                    0;
            maximumProgress =
                    1;
            detail =
                    "";
        }

        private boolean updateProgress(
                int newProgress,
                int newMaximum,
                String newDetail) {
            // Snapshot境界でも、0除算や範囲外進捗を再確認する。
            if (newMaximum <= 0
                    || newProgress < 0
                    || newProgress > newMaximum) {
                throw new IllegalArgumentException(
                        "invalid physical crafting progress");
            }
            String checkedNewDetail =
                    checkedDetail(
                            newDetail);
            boolean changed =
                    progress != newProgress
                            || maximumProgress != newMaximum
                            || !detail.equals(
                                    checkedNewDetail);
            progress =
                    newProgress;
            maximumProgress =
                    newMaximum;
            detail =
                    checkedNewDetail;
            return changed;
        }

        private void observeOutput(
                Map<AEKey, BigInteger> outputs,
                String newDetail) {
            observedOutputs =
                    checkedCounts(
                            outputs,
                            "observedOutputs",
                            false);
            progress =
                    PROGRESS_UNITS_PER_STEP;
            maximumProgress =
                    PROGRESS_UNITS_PER_STEP;
            state =
                    StepState.OUTPUT_OBSERVED;
            detail =
                    checkedDetail(
                            newDetail);
        }

        private void creditOutput() {
            state =
                    StepState.OUTPUT_CREDITED;
            progress =
                    PROGRESS_UNITS_PER_STEP;
            maximumProgress =
                    PROGRESS_UNITS_PER_STEP;
            detail =
                    "";
        }

        private void acknowledge() {
            state =
                    StepState.ACKNOWLEDGED;
            targetPosition =
                    null;
            progress =
                    PROGRESS_UNITS_PER_STEP;
            maximumProgress =
                    PROGRESS_UNITS_PER_STEP;
            reservedInputs =
                    Map.of();
            observedOutputs =
                    Map.of();
            detail =
                    "";
        }

        private void retryAnotherTarget() {
            state =
                    StepState.INPUTS_RESERVED;
            targetPosition =
                    null;
            progress =
                    0;
            maximumProgress =
                    1;
            observedOutputs =
                    Map.of();
            detail =
                    "";
        }

        private void cancel() {
            state =
                    StepState.CANCELLED;
            targetPosition =
                    null;
            progress =
                    0;
            maximumProgress =
                    1;
            reservedInputs =
                    Map.of();
            observedOutputs =
                    Map.of();
            detail =
                    "";
        }

        private int normalizedProgress() {
            // 出力がEscrowへ入った段は、Thread解放待ちでも100単位として表示する。
            if (state == StepState.OUTPUT_OBSERVED
                    || state == StepState.OUTPUT_CREDITED
                    || state == StepState.ACKNOWLEDGED) {
                return PROGRESS_UNITS_PER_STEP;
            }
            // 受理済みThreadだけNeoECO実進捗を割合換算する。
            if (state == StepState.ACCEPTED
                    && maximumProgress > 0) {
                return Math.min(
                        PROGRESS_UNITS_PER_STEP,
                        Math.multiplyExact(
                                        progress,
                                        PROGRESS_UNITS_PER_STEP)
                                / maximumProgress);
            }
            return 0;
        }

        private void validate() {
            boolean requiresTarget =
                    state == StepState.TARGET_SELECTED
                            || state == StepState.ACCEPTED
                            || state == StepState.OUTPUT_OBSERVED
                            || state == StepState.OUTPUT_CREDITED;
            boolean requiresReserved =
                    state == StepState.INPUTS_RESERVED
                            || requiresTarget;
            boolean requiresOutput =
                    state == StepState.OUTPUT_OBSERVED
                            || state == StepState.OUTPUT_CREDITED;
            boolean requiresDispatched =
                    state == StepState.ACCEPTED
                            || state == StepState.OUTPUT_OBSERVED
                            || state == StepState.OUTPUT_CREDITED
                            || state == StepState.ACKNOWLEDGED;
            // index、識別子、進捗、Target、予約入力、実出力の組合せを一意にする。
            if (index < 0
                    || payloadDigest.isBlank()
                    || routeCursor < 0
                    || maximumProgress <= 0
                    || progress < 0
                    || progress > maximumProgress
                    || requiresTarget
                            != (targetPosition != null)
                    || requiresReserved
                            != !reservedInputs.isEmpty()
                    || requiresOutput
                            != !observedOutputs.isEmpty()
                    || (requiresDispatched
                            && !dispatched)) {
                throw new IllegalArgumentException(
                        "invalid physical crafting recipe receipt");
            }
        }
    }

    private record PendingNetworkBatchMutation(
            UUID operationId,
            NetworkDirection direction,
            MutationPurpose purpose,
            Map<AEKey, BigInteger> amounts,
            Map<AEKey, BigInteger> beforeAmounts) {
        private PendingNetworkBatchMutation {
            Objects.requireNonNull(
                    operationId,
                    "operationId");
            Objects.requireNonNull(
                    direction,
                    "direction");
            Objects.requireNonNull(
                    purpose,
                    "purpose");
            amounts =
                    checkedCounts(
                            amounts,
                            "pending exact storage amounts",
                            false);
            Map<AEKey, BigInteger> checkedBefore =
                    new LinkedHashMap<>();
            Objects.requireNonNull(
                            beforeAmounts,
                            "beforeAmounts")
                    .forEach(
                            (key, before) -> {
                                Objects.requireNonNull(
                                        key,
                                        "pending exact storage before key");
                                // beforeは0を許すが、負数・API上限超過・重複は拒否する。
                                if (before == null
                                        || before.signum()
                                                < 0
                                        || before.bitLength()
                                                > CraftingTableBatchRequest
                                                        .MAXIMUM_COUNT_BITS
                                        || checkedBefore.putIfAbsent(
                                                        key,
                                                        before)
                                                != null) {
                                    throw new IllegalArgumentException(
                                            "invalid pending exact storage before amount");
                                }
                            });
            beforeAmounts =
                    immutableOrderedMap(
                            checkedBefore);
            // beforeと変更量は同じキー集合でなければ停止後に一括照合できない。
            if (!beforeAmounts.keySet()
                    .equals(
                            amounts.keySet())) {
                throw new IllegalArgumentException(
                        "pending exact storage keys do not match");
            }
            // 抽出Batchは全キーがbefore以下の場合だけ有効にする。
            if (direction == NetworkDirection.EXTRACT
                    && !containsAll(
                            beforeAmounts,
                            amounts)) {
                throw new IllegalArgumentException(
                        "pending exact extraction exceeds its before amounts");
            }
        }

        private Map<AEKey, BigInteger> afterAmounts() {
            Map<AEKey, BigInteger> result =
                    new LinkedHashMap<>();
            // 各キーを同じ方向へ一度だけ適用し、再起動照合用after一覧を作る。
            for (Map.Entry<AEKey, BigInteger> entry :
                    amounts.entrySet()) {
                BigInteger before =
                        beforeAmounts.get(
                                entry.getKey());
                result.put(
                        entry.getKey(),
                        direction == NetworkDirection.EXTRACT
                                ? before.subtract(
                                        entry.getValue())
                                : before.add(
                                        entry.getValue()));
            }
            return immutableOrderedMap(
                    result);
        }

        private boolean validFor(
                State state) {
            return switch (purpose) {
                case BOUNDARY_INPUT ->
                        state == State.RESERVING_BOUNDARY_INPUTS;
                case FINAL_OUTPUT ->
                        state == State.RETURNING_RESULTS;
                case CANCELLED_ESCROW ->
                        state == State.RETURNING_CANCELLED_ESCROW;
            };
        }

        private CompoundTag save() {
            CompoundTag owner =
                    new CompoundTag();
            owner.putUUID(
                    "operationId",
                    operationId);
            owner.putString(
                    "direction",
                    direction.name());
            owner.putString(
                    "purpose",
                    purpose.name());
            owner.put(
                    "amounts",
                    encodeCounts(
                            amounts));
            owner.put(
                    "beforeAmounts",
                    encodeNonNegativeCounts(
                            beforeAmounts));
            return owner;
        }

        private static PendingNetworkBatchMutation load(
                CompoundTag owner) {
            // UUIDが欠ける操作は、停止後に同じ境界を照合できない。
            if (!owner.hasUUID(
                    "operationId")) {
                throw new IllegalArgumentException(
                        "pending exact storage mutation has no operation id");
            }
            try {
                return new PendingNetworkBatchMutation(
                        owner.getUUID(
                                "operationId"),
                        NetworkDirection.valueOf(
                                owner.getString(
                                        "direction")),
                        MutationPurpose.valueOf(
                                owner.getString(
                                        "purpose")),
                        decodeCounts(
                                owner,
                                "amounts"),
                        decodeNonNegativeCounts(
                                owner,
                                "beforeAmounts"));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException(
                        "invalid pending exact storage mutation",
                        invalid);
            }
        }
    }

    private record ResolvedStep(
            ExactCraftingStep step,
            IPatternDetails pattern,
            ExactPatternFormula formula,
            Map<AEKey, BigInteger> inputTotals,
            Map<AEKey, BigInteger> expectedOutputs) {
        private ResolvedStep {
            Objects.requireNonNull(
                    step,
                    "step");
            Objects.requireNonNull(
                    pattern,
                    "pattern");
            Objects.requireNonNull(
                    formula,
                    "formula");
            inputTotals =
                    checkedCounts(
                            inputTotals,
                            "resolved input totals",
                            false);
            expectedOutputs =
                    checkedCounts(
                            expectedOutputs,
                            "resolved output totals",
                            false);
        }
    }

    private record SelectedTarget(
            long position,
            int nextRouteCursor) {
    }

    private record StepAdvance(
            boolean changed,
            boolean quarantined,
            String detail) {
        private StepAdvance {
            detail =
                    Objects.requireNonNull(
                            detail,
                            "detail");
        }

        private static StepAdvance updated() {
            return new StepAdvance(
                    true,
                    false,
                    "");
        }

        private static StepAdvance waiting() {
            return new StepAdvance(
                    false,
                    false,
                    "");
        }

        private static StepAdvance quarantined(
                String detail) {
            return new StepAdvance(
                    false,
                    true,
                    Objects.requireNonNull(
                            detail,
                            "detail"));
        }
    }

    public record TickOutcome(
            Kind kind,
            String detail) {
        public TickOutcome {
            Objects.requireNonNull(
                    kind,
                    "kind");
            detail =
                    Objects.requireNonNull(
                            detail,
                            "detail");
        }

        public static TickOutcome changed() {
            return new TickOutcome(
                    Kind.CHANGED,
                    "");
        }

        public static TickOutcome waiting(
                String detail) {
            return new TickOutcome(
                    Kind.WAITING,
                    Objects.requireNonNull(
                            detail,
                            "detail"));
        }

        public static TickOutcome complete() {
            return new TickOutcome(
                    Kind.COMPLETE,
                    "");
        }

        public static TickOutcome cancelled() {
            return new TickOutcome(
                    Kind.CANCELLED,
                    "");
        }

        public static TickOutcome quarantined(
                String detail) {
            return new TickOutcome(
                    Kind.QUARANTINED,
                    Objects.requireNonNull(
                            detail,
                            "detail"));
        }
    }

    public enum Kind {
        CHANGED,
        WAITING,
        COMPLETE,
        CANCELLED,
        QUARANTINED
    }

    public record AccountingSnapshot(
            Map<String, BigInteger> plannedPatternExecutions,
            Map<String, BigInteger> dispatchedPatternExecutions,
            Map<AEKey, BigInteger> expectedOutputs,
            Map<AEKey, BigInteger> introducedOutputs,
            Map<AEKey, BigInteger> creditedOutputs,
            boolean finalOutputReturned) {
        public AccountingSnapshot {
            plannedPatternExecutions = checkedCounts(
                    plannedPatternExecutions,
                    "plannedPatternExecutions",
                    false);
            dispatchedPatternExecutions = checkedCounts(
                    dispatchedPatternExecutions,
                    "dispatchedPatternExecutions",
                    true);
            expectedOutputs = checkedCounts(
                    expectedOutputs,
                    "expectedOutputs",
                    true);
            introducedOutputs = checkedCounts(
                    introducedOutputs,
                    "introducedOutputs",
                    true);
            creditedOutputs = checkedCounts(
                    creditedOutputs,
                    "creditedOutputs",
                    true);
            // 配送済みPatternと受領済み出力は、それぞれ計画総量を超えてはならない。
            if (!containsAll(
                            plannedPatternExecutions,
                            dispatchedPatternExecutions)
                    || !containsAll(
                            expectedOutputs,
                            introducedOutputs)
                    || !containsAll(
                            introducedOutputs,
                            creditedOutputs)) {
                throw new IllegalArgumentException(
                        "physical accounting snapshot exceeds its plan");
            }
        }
    }
}

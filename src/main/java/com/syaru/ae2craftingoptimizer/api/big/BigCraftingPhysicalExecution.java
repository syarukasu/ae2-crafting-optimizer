package com.syaru.ae2craftingoptimizer.api.big;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.api.batch.ExactPatternFormula;
import com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatch;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.Ae2BigCraftingPlanFactory;
import com.syaru.ae2craftingoptimizer.engine.Ae2CompiledCraftingGraphCache;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import com.syaru.ae2craftingoptimizer.engine.ExactCraftingJobState;
import com.syaru.ae2craftingoptimizer.engine.SelectedBranchPhysicalPlan;
import com.syaru.ae2craftingoptimizer.engine.BigCapacityCraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.StalePlanningSnapshotException;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import com.syaru.ae2craftingoptimizer.engine.craftingtable.CraftingTableBatchTargetResolver;
import com.syaru.ae2craftingoptimizer.engine.craftingtable.PhysicalCraftingTreeTransaction;
import com.syaru.ae2craftingoptimizer.engine.vector.VectorBatchPlanValidator;
import com.syaru.ae2craftingoptimizer.engine.vector.VectorBatchPlanner;
import com.syaru.ae2craftingoptimizer.integration.ExactBoundaryRoutePreflight;
import com.syaru.ae2craftingoptimizer.integration.ExactVectorGridTickBudget;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * Issue #182: CPU-independent access to the existing receipt-backed physical
 * transaction. The caller owns its CPU, ticking, persistence and link lifecycle.
 * Save after each advance, including waits, before advancing again.
 */
public final class BigCraftingPhysicalExecution {
    public static final int API_VERSION = 1;
    private final BigInteger reservedBytes;
    private final PhysicalCraftingTreeTransaction transaction;
    private String detail = "prepared";

    BigCraftingPhysicalExecution(BigInteger reservedBytes,
            PhysicalCraftingTreeTransaction transaction) {
        // CPU予約量は正数かつ既存のexact上限内に限定する。
        if (reservedBytes.signum() <= 0
                || reservedBytes.bitLength() > ACOConfig.getBigIntegerMaximumBits()) {
            throw new IllegalArgumentException("invalid exact CPU reservation");
        }
        this.reservedBytes = reservedBytes;
        this.transaction = transaction;
    }

    /** Only byte-total overflow is delegated to the add-on's original long executor. */
    public static boolean validateNativePlan(ICraftingPlan plan, IGrid grid) {
        var metadata = Ae2CraftingPlanSidecars.metadata(plan).orElse(null);
        // 数量までwideな計画をnative long実行へ渡さない。
        if (!(metadata instanceof BigCapacityCraftingPlan capacity)) return false;
        // 不足または世代不一致の容量計画は提出前に拒否する。
        if (capacity.simulation() || !capacity.validateForSubmission(grid).valid()) {
            throw new IllegalArgumentException("capacity-only plan is missing or stale");
        }
        return true;
    }

    /** Pure preflight: does not extract input, start a worker or claim a CPU. */
    public static BigCraftingPhysicalExecution prepare(ICraftingPlan plan, UUID jobId,
            IGrid grid, Level level, IActionSource source) {
        // 無効な計算プロファイルから新規所有権を取得させない。
        if (!BigCraftingEngineApi.isCalculationProfileActive()) {
            throw new IllegalArgumentException("exact planning is disabled");
        }
        // 物理実行の無効化は新規開始だけに適用する。
        if (!ACOConfig.enableExactBigIntegerPhysicalExecution()) {
            throw new IllegalArgumentException("exact physical execution is disabled");
        }
        var exact = Ae2CraftingPlanSidecars.bigInteger(plan).orElseThrow(
                () -> new IllegalArgumentException("plan has no wide-count sidecar"));
        // 不足または失効した数量計画を実行しない。
        if (exact.simulation() || !exact.validateForSubmission(grid).valid()) {
            throw new IllegalArgumentException("exact plan is missing or stale");
        }
        // Consumer登録だけでは搬入出経路の証明にならないため、所有権取得前に検証する。
        var route = ExactBoundaryRoutePreflight.check(grid, exact, source);
        if (!route.viable()) {
            throw new IllegalArgumentException(route.detail());
        }
        var graph = Ae2CompiledCraftingGraphCache.getOrCompile(grid, level);
        var state = ExactCraftingJobState.fromPlan(exact);
        int bits = ACOConfig.getBigIntegerMaximumBits();
        PreparedVectorBatch prepared;
        if (state.selectedBranch().isPresent()) {
            prepared = SelectedBranchPhysicalPlan.forJob(state.selectedBranch().orElseThrow(), jobId);
            SelectedBranchPhysicalPlan.validateAccounting(prepared, exact.exactPlan());
            SelectedBranchPhysicalPlan.validateBindings(prepared, graph::pattern, level, bits);
        } else {
            var program = graph.rootProgram(state.requestedKey()).orElseThrow(
                    () -> new IllegalArgumentException("exact root program is unavailable"));
            // 計画時と異なるレシピへ入力を引き渡さない。
            if (!state.programFingerprint().equals(Ae2BigCraftingPlanFactory.programFingerprint(program))) {
                throw new IllegalArgumentException("exact root program changed");
            }
            var inventory = program.captureBigInventory(
                    key -> state.plannedInventory().getOrDefault(key, BigInteger.ZERO), bits);
            prepared = VectorBatchPlanner.prepare(UUID.randomUUID(), jobId,
                    program, inventory, state.requestedAmount(), state.programFingerprint(),
                    ProviderPatternGenerationTracker.generation(), graph.recipeGeneration(), bits);
        }
        VectorBatchPlanValidator.validate(prepared, bits,
                ACOConfig.getExactVectorMaximumPatternNodes(),
                ACOConfig.getExactVectorMaximumInputKeys(),
                ACOConfig.getExactVectorMaximumOutputKeys());
        // Receipt対応workerを探索できるAE2サービスだけを受け付ける。
        if (!(grid.getCraftingService() instanceof CraftingService service)) {
            throw new IllegalArgumentException("crafting service is unavailable");
        }
        // 全加工段階に、計画通りの入力を扱うReceipt対応workerがあることを確認する。
        for (var step : prepared.craftingSteps()) {
            var pattern = graph.pattern(step.patternId());
            // 未ロードまたは非対応の段階は、素材を移す前に拒否する。
            if (pattern == null || ExactPatternFormula.tryCreate(pattern, level, step.selectedInputs()).isEmpty()
                    || !CraftingTableBatchTargetResolver.resolve(service, pattern, level).ready()) {
                throw new IllegalArgumentException("no loaded receipt-backed worker for " + step.patternId());
            }
        }
        var transaction = PhysicalCraftingTreeTransaction.create(prepared,
                PhysicalCraftingTreeTransaction.capturePatternAccounting(prepared, graph, level));
        // 物理取引の予定会計が提出されたexact計画と一致していることを確認する。
        if (!transaction.accountingSnapshot().plannedPatternDefinitions().equals(state.taskTotals())
                || !state.initialWaiting().isEmpty()) {
            throw new IllegalArgumentException("physical plan does not match exact task accounting");
        }
        return new BigCraftingPhysicalExecution(exact.exactBytes(), transaction);
    }

    /** Restoring an owned transaction does not require that new jobs are enabled. */
    public static BigCraftingPhysicalExecution load(CompoundTag saved) {
        // 未対応形式を既知のReceipt状態として解釈しない。
        if (saved.getInt("schema") != 1) {
            throw new IllegalArgumentException("unsupported physical execution schema");
        }
        String bytes = saved.getString("reservedBytes");
        // 既存の最大16,384桁の保存契約を守り、非正規な整数表現を拒否する。
        if (bytes.length() > 16384 || !bytes.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("invalid saved exact reservation");
        }
        return new BigCraftingPhysicalExecution(new BigInteger(bytes),
                PhysicalCraftingTreeTransaction.load(saved.getCompound("transaction")));
    }

    public CompoundTag save() {
        CompoundTag saved = new CompoundTag();
        saved.putInt("schema", 1);
        saved.putString("reservedBytes", reservedBytes.toString());
        saved.put("transaction", transaction.save());
        return saved;
    }

    /** The same grid-wide budget is shared with ordinary ACO exact jobs. */
    public void advance(IGrid grid, Level level, IActionSource source) {
        int budget = ExactVectorGridTickBudget.claimActiveStages(grid,
                Math.max(1, transaction.plan().craftingSteps().size()));
        // Grid共通予算を使い切ったtickでは取引を進めない。
        if (budget == 0) {
            detail = "waiting for grid execution budget";
            return;
        }
        boolean advanced = false;
        try {
            var graph = Ae2CompiledCraftingGraphCache.getOrCompile(grid, level);
            advanced = true;
            detail = transaction.tick(grid, level, source, graph, budget).detail();
        } catch (StalePlanningSnapshotException stale) {
            // 世代変化によるSnapshot待ちは会計破損ではなく、所有済みReceiptを保持する。
            detail = "waiting for a stable crafting snapshot";
        } catch (RuntimeException | LinkageError failure) {
            transaction.quarantineForAccounting("external execution failed: " + failure);
            detail = failure.toString();
        } finally {
            ExactVectorGridTickBudget.settleActiveStageClaim(grid, budget,
                    advanced ? transaction.lastConsumedOperations() : 0);
        }
    }

    public void requestCancellation() {
        transaction.requestCancellation();
    }

    public BigInteger reservedBytes() { return reservedBytes; }
    public String state() { return transaction.state().name(); }
    public String detail() { return detail == null ? "" : detail; }
    public UUID jobId() { return transaction.plan().parentJobId(); }
    public int progressNumerator() { return transaction.progressNumerator(); }
    public int progressDenominator() { return transaction.progressDenominator(); }
    public BigInteger remainingOutput() {
        return transaction.accountingSnapshot().finalOutputReturned()
                ? BigInteger.ZERO : transaction.plan().requestedAmount();
    }
    public Map<AEKey, BigInteger> storedItems() { return transaction.escrowSnapshot(); }
    public Map<AEKey, BigInteger> waitingItems() {
        var accounting = transaction.accountingSnapshot();
        return difference(accounting.introducedOutputs(), accounting.creditedOutputs());
    }
    public Map<AEKey, BigInteger> pendingItems() {
        var accounting = transaction.accountingSnapshot();
        return difference(accounting.expectedOutputs(), accounting.introducedOutputs());
    }

    private static Map<AEKey, BigInteger> difference(Map<AEKey, BigInteger> total,
            Map<AEKey, BigInteger> done) {
        Map<AEKey, BigInteger> remaining = new LinkedHashMap<>();
        total.forEach((key, amount) -> {
            BigInteger value = amount.subtract(done.getOrDefault(key, BigInteger.ZERO));
            // Receiptが予定量を超えた場合は、不足や完了へ読み替えない。
            if (value.signum() < 0) throw new IllegalStateException("receipt count exceeds total");
            // 未処理の正確な残量だけを公開する。
            if (value.signum() > 0) remaining.put(key, value);
        });
        return Map.copyOf(remaining);
    }
}

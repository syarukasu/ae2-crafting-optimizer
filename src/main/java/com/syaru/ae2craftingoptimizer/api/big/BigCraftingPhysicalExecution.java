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
        if (!(metadata instanceof BigCapacityCraftingPlan capacity)) return false;
        if (capacity.simulation() || !capacity.validateForSubmission(grid).valid()) {
            throw new IllegalArgumentException("capacity-only plan is missing or stale");
        }
        return true;
    }

    /** Pure preflight: does not extract input, start a worker or claim a CPU. */
    public static BigCraftingPhysicalExecution prepare(ICraftingPlan plan, UUID jobId,
            IGrid grid, Level level, IActionSource source) {
        if (!BigCraftingEngineApi.isCalculationProfileActive()) {
            throw new IllegalArgumentException("exact planning is disabled");
        }
        if (!ACOConfig.enableExactBigIntegerPhysicalExecution()) {
            throw new IllegalArgumentException("exact physical execution is disabled");
        }
        var exact = Ae2CraftingPlanSidecars.bigInteger(plan).orElseThrow(
                () -> new IllegalArgumentException("plan has no wide-count sidecar"));
        if (exact.simulation() || !exact.validateForSubmission(grid).valid()) {
            throw new IllegalArgumentException("exact plan is missing or stale");
        }
        // A registered consumer is not proof that the physical storage route exists.
        var route = ExactBoundaryRoutePreflight.check(grid, exact, source);
        if (!route.viable()) {
            throw new IllegalArgumentException(route.detail());
        }
        var graph = Ae2CompiledCraftingGraphCache.getOrCompile(grid, level);
        var state = ExactCraftingJobState.fromPlan(exact);
        var program = graph.rootProgram(state.requestedKey()).orElseThrow(
                () -> new IllegalArgumentException("exact root program is unavailable"));
        if (!state.programFingerprint().equals(Ae2BigCraftingPlanFactory.programFingerprint(program))) {
            throw new IllegalArgumentException("exact root program changed");
        }
        int bits = ACOConfig.getBigIntegerMaximumBits();
        var inventory = program.captureBigInventory(
                key -> state.plannedInventory().getOrDefault(key, BigInteger.ZERO), bits);
        PreparedVectorBatch prepared = VectorBatchPlanner.prepare(UUID.randomUUID(), jobId,
                program, inventory, state.requestedAmount(), state.programFingerprint(),
                ProviderPatternGenerationTracker.generation(), graph.recipeGeneration(), bits);
        VectorBatchPlanValidator.validate(prepared, bits,
                ACOConfig.getExactVectorMaximumPatternNodes(),
                ACOConfig.getExactVectorMaximumInputKeys(),
                ACOConfig.getExactVectorMaximumOutputKeys());
        if (!(grid.getCraftingService() instanceof CraftingService service)) {
            throw new IllegalArgumentException("crafting service is unavailable");
        }
        for (var step : prepared.craftingSteps()) {
            var pattern = graph.pattern(step.patternId());
            if (pattern == null || ExactPatternFormula.tryCreate(pattern, level, step.selectedInputs()).isEmpty()
                    || !CraftingTableBatchTargetResolver.resolve(service, pattern, level).ready()) {
                throw new IllegalArgumentException("no loaded receipt-backed worker for " + step.patternId());
            }
        }
        var transaction = PhysicalCraftingTreeTransaction.create(prepared,
                PhysicalCraftingTreeTransaction.capturePatternAccounting(prepared, graph, level));
        if (!transaction.accountingSnapshot().plannedPatternDefinitions().equals(state.taskTotals())
                || !state.initialWaiting().isEmpty()) {
            throw new IllegalArgumentException("physical plan does not match exact task accounting");
        }
        return new BigCraftingPhysicalExecution(exact.exactBytes(), transaction);
    }

    /** Restoring an owned transaction does not require that new jobs are enabled. */
    public static BigCraftingPhysicalExecution load(CompoundTag saved) {
        if (saved.getInt("schema") != 1) {
            throw new IllegalArgumentException("unsupported physical execution schema");
        }
        String bytes = saved.getString("reservedBytes");
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
            // A provider-generation race is retryable, not evidence of an accounting conflict.
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
            if (value.signum() < 0) throw new IllegalStateException("receipt count exceeds total");
            if (value.signum() > 0) remaining.put(key, value);
        });
        return Map.copyOf(remaining);
    }
}

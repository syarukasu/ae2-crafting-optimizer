package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ICraftingInventory;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.access.CraftingJobTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingLogicTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingOwnerTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingTaskProgressAccess;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchAdapter;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchApi;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchBudget;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchResult;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.scheduler.PatternProviderRoutingCache;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Coordinates exact accepted-execution-count batches without treating aggregate inventory
 * insertion as machine recipe completion.
 */
public final class BatchedCraftingExecutor {
    public static final int NOT_HANDLED = -1;

    private static final Set<String> ACCESS_FAILURES_LOGGED =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private BatchedCraftingExecutor() {
    }

    public static int tryExecute(
            Object logic,
            int maxPatterns,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level) {
        if (!ACOConfig.enableTransactionalPatternBatching()
                || logic == null
                || maxPatterns < 2
                || craftingService == null
                || energyService == null
                || level == null) {
            return NOT_HANDLED;
        }

        BatchExtraction pendingExtraction = null;
        ICraftingInventory pendingInventory = null;
        boolean commitStarted = false;
        int acceptedTotal = 0;
        int transactionCount = 0;
        boolean instantDispatch = ACOConfig.enableInstantPatternDispatch();
        int maximumTransactions = instantDispatch
                ? ACOConfig.getMaxInstantPatternDispatchTransactions()
                : 1;
        long deadlineNanos = instantDispatch
                ? deadlineAfterMillis(ACOConfig.getInstantPatternDispatchTimeBudgetMillis())
                : Long.MAX_VALUE;
        try {
            if (!(logic instanceof CraftingLogicTransactionAccess logicAccess)) {
                logAccessFailure(logic.getClass(), "required CPU access mixin was not applied", null);
                return NOT_HANDLED;
            }
            Object job = logicAccess.aco$getExecutingJob();
            CraftingOwnerTransactionAccess owner = logicAccess.aco$getCraftingOwner();
            ICraftingInventory inventory = logicAccess.aco$getCraftingInventory();
            if (job == null || owner == null || inventory == null) {
                return NOT_HANDLED;
            }
            pendingInventory = inventory;

            if (!(job instanceof CraftingJobTransactionAccess jobAccess)) {
                logAccessFailure(logic.getClass(), "required crafting job access mixin was not applied", null);
                return NOT_HANDLED;
            }
            Map<IPatternDetails, Object> tasks = jobAccess.aco$getTasks();
            ICraftingInventory waitingFor = jobAccess.aco$getWaitingFor();
            if (tasks == null || waitingFor == null || tasks.isEmpty()) {
                return NOT_HANDLED;
            }

            Iterator<Map.Entry<IPatternDetails, Object>> iterator = tasks.entrySet().iterator();
            while (iterator.hasNext()
                    && acceptedTotal < maxPatterns
                    && transactionCount < maximumTransactions
                    && hasTimeRemaining(deadlineNanos)) {
                Map.Entry<IPatternDetails, Object> task = iterator.next();
                IPatternDetails details = task.getKey();
                if (details == null || !(task.getValue() instanceof CraftingTaskProgressAccess progress)) {
                    continue;
                }
                long remainingExecutions = progress.aco$getTaskProgress();
                if (remainingExecutions < 2L) {
                    continue;
                }

                ExactPatternPlan plan = ExactPatternPlan.create(details, level);
                if (plan == null) {
                    continue;
                }

                while (remainingExecutions >= 2L
                        && acceptedTotal < maxPatterns
                        && transactionCount < maximumTransactions
                        && hasTimeRemaining(deadlineNanos)) {
                    ProviderSelection selection = selectProvider(craftingService, details, plan, level);
                    if (selection == ProviderSelection.UNSUPPORTED) {
                        return acceptedTotal > 0 ? acceptedTotal : NOT_HANDLED;
                    }
                    if (selection == null) {
                        break;
                    }

                    long operationBudget = (long) maxPatterns - acceptedTotal;
                    long requestedBatch = Math.min(
                            Math.min(remainingExecutions, operationBudget),
                            (long) ACOConfig.getMaxTransactionalPatternBatchExecutions());
                    requestedBatch = plan.safeBatchSize(requestedBatch);
                    requestedBatch = PatternBatchApi.limitExecutions(
                            selection.adapter(), selection.context(), requestedBatch);
                    requestedBatch = limitByEnergy(plan.inputsPerExecution(), requestedBatch, energyService);
                    requestedBatch = limitByInventory(plan.totalInputsPerExecution(), requestedBatch, inventory);
                    if (requestedBatch < 2L) {
                        break;
                    }

                    pendingExtraction = extractBatch(plan, inventory, requestedBatch);
                    if (pendingExtraction == null) {
                        break;
                    }

                    transactionCount++;
                    commitStarted = true;
                    PatternBatchResult result = PatternBatchApi.commit(
                            selection.adapter(),
                            selection.context(),
                            new PatternBatchBudget(requestedBatch, deadlineNanos));
                    long accepted = result.acceptedExecutions();
                    if (accepted < 0L || accepted > requestedBatch) {
                        throw new IllegalStateException(
                                "Pattern batch adapter " + selection.adapter().id()
                                        + " returned invalid accepted count " + accepted
                                        + " for request " + requestedBatch);
                    }
                    if (accepted == 0L) {
                        BatchExtraction rejectedExtraction = pendingExtraction;
                        CraftingCpuHelper.reinjectPatternInputs(
                                inventory,
                                rejectedExtraction.aggregateInputs());
                        pendingExtraction = null;
                        commitStarted = false;
                        break;
                    }

                    long unaccepted = requestedBatch - accepted;
                    if (unaccepted > 0L) {
                        CraftingCpuHelper.reinjectPatternInputs(
                                inventory,
                                scaleCounters(plan.inputsPerExecution(), unaccepted));
                    }

                    double acceptedPower = CraftingCpuHelper.calculatePatternPower(
                            scaleCounters(plan.inputsPerExecution(), accepted));
                    energyService.extractAEPower(
                            acceptedPower,
                            Actionable.MODULATE,
                            PowerMultiplier.CONFIG);

                    KeyCounter acceptedOutputs = scaleCounter(plan.outputsPerExecution(), accepted);
                    for (var expectedOutput : acceptedOutputs) {
                        waitingFor.insert(
                                expectedOutput.getKey(),
                                expectedOutput.getLongValue(),
                                Actionable.MODULATE);
                    }

                    remainingExecutions -= accepted;
                    progress.aco$setTaskProgress(remainingExecutions);
                    owner.aco$markCraftingOwnerDirty();
                    OptimizationMetrics.recordTransactionalPatternBatch(accepted);
                    acceptedTotal = Math.addExact(acceptedTotal, Math.toIntExact(accepted));
                    pendingExtraction = null;
                    commitStarted = false;

                    if (!instantDispatch) {
                        break;
                    }
                }

                if (remainingExecutions <= 0L) {
                    iterator.remove();
                }
            }
            if (acceptedTotal > 0) {
                return acceptedTotal;
            }
            return transactionCount >= maximumTransactions || !hasTimeRemaining(deadlineNanos)
                    ? 0
                    : NOT_HANDLED;
        } catch (Throwable throwable) {
            if (pendingExtraction != null && pendingInventory != null && !commitStarted) {
                CraftingCpuHelper.reinjectPatternInputs(
                        pendingInventory,
                        pendingExtraction.aggregateInputs());
            }
            if (commitStarted) {
                throw new IllegalStateException(
                        "ACO pattern batch adapter entered commit but AE2 accounting could not be completed safely.",
                        throwable);
            }
            logAccessFailure(logic.getClass(), "transactional batch execution fell back to AE2", throwable);
            return acceptedTotal > 0 ? acceptedTotal : NOT_HANDLED;
        }
    }

    private static long deadlineAfterMillis(int milliseconds) {
        long now = System.nanoTime();
        long budget = milliseconds * 1_000_000L;
        return now > Long.MAX_VALUE - budget ? Long.MAX_VALUE : now + budget;
    }

    private static boolean hasTimeRemaining(long deadlineNanos) {
        return deadlineNanos == Long.MAX_VALUE || System.nanoTime() < deadlineNanos;
    }

    @Nullable
    private static ProviderSelection selectProvider(
            CraftingService craftingService,
            IPatternDetails details,
            ExactPatternPlan plan,
            Level level) {
        boolean sawAvailableProvider = false;
        for (ICraftingProvider provider : PatternProviderRoutingCache.candidates(craftingService, details)) {
            if (provider.isBusy()) {
                continue;
            }
            sawAvailableProvider = true;
            PatternProviderBatchEligibility.BatchTarget target =
                    PatternProviderBatchEligibility.inspect(provider, details, level);
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
            PatternBatchAdapter adapter = PatternBatchApi.find(context).orElse(null);
            if (adapter == null) {
                continue;
            }
            return new ProviderSelection(adapter, context);
        }
        return sawAvailableProvider ? ProviderSelection.UNSUPPORTED : null;
    }

    private static long limitByEnergy(
            KeyCounter[] inputsPerExecution,
            long requestedExecutions,
            IEnergyService energyService) {
        double powerPerExecution = CraftingCpuHelper.calculatePatternPower(inputsPerExecution);
        if (!Double.isFinite(powerPerExecution) || powerPerExecution < 0.0D) {
            return 0L;
        }
        if (powerPerExecution <= 0.0D) {
            return requestedExecutions;
        }
        double requestedPower = powerPerExecution * requestedExecutions;
        if (!Double.isFinite(requestedPower)) {
            requestedPower = Double.MAX_VALUE;
        }
        double available = energyService.extractAEPower(
                requestedPower,
                Actionable.SIMULATE,
                PowerMultiplier.CONFIG);
        if (available >= requestedPower - 0.01D) {
            return requestedExecutions;
        }
        long energyLimited = (long) Math.floor((available + 0.01D) / powerPerExecution);
        return Math.max(0L, Math.min(requestedExecutions, energyLimited));
    }

    private static long limitByInventory(
            KeyCounter totalInputsPerExecution,
            long requestedExecutions,
            ICraftingInventory inventory) {
        long limited = requestedExecutions;
        for (var entry : totalInputsPerExecution) {
            long amountPerExecution = entry.getLongValue();
            long requested = Math.multiplyExact(amountPerExecution, limited);
            long available = inventory.extract(entry.getKey(), requested, Actionable.SIMULATE);
            limited = Math.min(limited, available / amountPerExecution);
            if (limited < 2L) {
                return limited;
            }
        }
        return limited;
    }

    @Nullable
    private static BatchExtraction extractBatch(
            ExactPatternPlan plan,
            ICraftingInventory inventory,
            long executions) {
        KeyCounter[] aggregate = scaleCounters(plan.inputsPerExecution(), executions);
        KeyCounter[] extracted = new KeyCounter[aggregate.length];
        try {
            for (int index = 0; index < aggregate.length; index++) {
                KeyCounter extractedForInput = extracted[index] = new KeyCounter();
                for (var entry : aggregate[index]) {
                    long requested = entry.getLongValue();
                    long simulated = inventory.extract(entry.getKey(), requested, Actionable.SIMULATE);
                    if (simulated != requested) {
                        CraftingCpuHelper.reinjectPatternInputs(inventory, extracted);
                        return null;
                    }
                    long actual = inventory.extract(entry.getKey(), requested, Actionable.MODULATE);
                    if (actual != requested) {
                        throw new IllegalStateException("AE2 crafting inventory simulation disagreed with extraction");
                    }
                    extractedForInput.add(entry.getKey(), actual);
                }
            }
            return new BatchExtraction(extracted);
        } catch (Throwable throwable) {
            CraftingCpuHelper.reinjectPatternInputs(inventory, extracted);
            throw throwable;
        }
    }

    private static KeyCounter[] scaleCounters(KeyCounter[] source, long multiplier) {
        KeyCounter[] scaled = new KeyCounter[source.length];
        for (int index = 0; index < source.length; index++) {
            scaled[index] = scaleCounter(source[index], multiplier);
        }
        return scaled;
    }

    private static KeyCounter scaleCounter(KeyCounter source, long multiplier) {
        KeyCounter scaled = new KeyCounter();
        for (var entry : source) {
            scaled.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), multiplier));
        }
        return scaled;
    }

    private static void logAccessFailure(Class<?> logicClass, String message, @Nullable Throwable throwable) {
        String key = logicClass.getName() + ":" + message;
        if (!ACCESS_FAILURES_LOGGED.add(key)) {
            return;
        }
        if (throwable == null) {
            AE2CraftingOptimizer.LOGGER.warn(
                    "ACO transactional pattern batching disabled for {}: {}",
                    logicClass.getName(),
                    message);
        } else {
            AE2CraftingOptimizer.LOGGER.warn(
                    "ACO transactional pattern batching disabled for {}: {} ({})",
                    logicClass.getName(),
                    message,
                    throwable.toString());
        }
    }

    private record ExactInput(AEKey key, long amountPerExecution) {
    }

    private record ExactPatternPlan(
            ExactInput[] inputs,
            KeyCounter[] inputsPerExecution,
            KeyCounter totalInputsPerExecution,
            KeyCounter outputsPerExecution) {
        @Nullable
        static ExactPatternPlan create(IPatternDetails details, Level level) {
            if (!details.supportsPushInputsToExternalInventory()) {
                return null;
            }
            IPatternDetails.IInput[] patternInputs = details.getInputs();
            ExactInput[] exactInputs = new ExactInput[patternInputs.length];
            KeyCounter[] perExecution = new KeyCounter[patternInputs.length];
            KeyCounter totalInputs = new KeyCounter();
            try {
                for (int index = 0; index < patternInputs.length; index++) {
                    IPatternDetails.IInput input = patternInputs[index];
                    var possibleInputs = input.getPossibleInputs();
                    if (input.getMultiplier() <= 0L || possibleInputs.length != 1) {
                        return null;
                    }
                    var possible = possibleInputs[0];
                    if (possible.amount() <= 0L
                            || !input.isValid(possible.what(), level)
                            || input.getRemainingKey(possible.what()) != null) {
                        return null;
                    }
                    long amount = Math.multiplyExact(possible.amount(), input.getMultiplier());
                    exactInputs[index] = new ExactInput(possible.what(), amount);
                    KeyCounter counter = perExecution[index] = new KeyCounter();
                    counter.add(possible.what(), amount);
                    addExact(totalInputs, possible.what(), amount);
                }

                KeyCounter outputs = new KeyCounter();
                for (var output : details.getOutputs()) {
                    if (output.amount() <= 0L) {
                        return null;
                    }
                    addExact(outputs, output.what(), output.amount());
                }
                return new ExactPatternPlan(exactInputs, perExecution, totalInputs, outputs);
            } catch (ArithmeticException ignored) {
                return null;
            }
        }

        long safeBatchSize(long requested) {
            long safe = requested;
            for (var input : totalInputsPerExecution) {
                safe = Math.min(safe, Long.MAX_VALUE / input.getLongValue());
            }
            for (var output : outputsPerExecution) {
                safe = Math.min(safe, Long.MAX_VALUE / output.getLongValue());
            }
            return safe;
        }

        private static void addExact(KeyCounter counter, AEKey key, long amount) {
            counter.set(key, Math.addExact(counter.get(key), amount));
        }
    }

    private record BatchExtraction(KeyCounter[] aggregateInputs) {
    }

    private record ProviderSelection(PatternBatchAdapter adapter, PatternBatchContext context) {
        private static final ProviderSelection UNSUPPORTED = new ProviderSelection(null, null);
    }

}

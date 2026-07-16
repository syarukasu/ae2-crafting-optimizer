package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ICraftingInventory;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class BatchedCraftingExecutor {
    public static final int NOT_HANDLED = -1;

    private static final Set<String> ACCESS_FAILURES_LOGGED =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final ClassValue<LogicAccess> LOGIC_ACCESS = new ClassValue<>() {
        @Override
        protected LogicAccess computeValue(Class<?> type) {
            return LogicAccess.create(type);
        }
    };
    private static final ClassValue<JobAccess> JOB_ACCESS = new ClassValue<>() {
        @Override
        protected JobAccess computeValue(Class<?> type) {
            return JobAccess.create(type);
        }
    };
    private static final ClassValue<ProgressAccess> PROGRESS_ACCESS = new ClassValue<>() {
        @Override
        protected ProgressAccess computeValue(Class<?> type) {
            return ProgressAccess.create(type);
        }
    };
    private static final ClassValue<OwnerAccess> OWNER_ACCESS = new ClassValue<>() {
        @Override
        protected OwnerAccess computeValue(Class<?> type) {
            return OwnerAccess.create(type);
        }
    };

    private BatchedCraftingExecutor() {
    }

    public static int tryExecute(
            Object logic,
            int maxPatterns,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level) {
        if (!ACOConfig.enablePatternMicroBatching()
                || logic == null
                || maxPatterns < 2
                || craftingService == null
                || energyService == null
                || level == null) {
            return NOT_HANDLED;
        }

        boolean externalPushAccepted = false;
        try {
            LogicAccess logicAccess = LOGIC_ACCESS.get(logic.getClass());
            if (!logicAccess.supported()) {
                logAccessFailure(logic.getClass(), "required CPU fields were not found", null);
                return NOT_HANDLED;
            }
            Object job = logicAccess.job(logic);
            Object owner = logicAccess.owner(logic);
            ICraftingInventory inventory = logicAccess.inventory(logic);
            if (job == null || owner == null || inventory == null) {
                return NOT_HANDLED;
            }

            JobAccess jobAccess = JOB_ACCESS.get(job.getClass());
            OwnerAccess ownerAccess = OWNER_ACCESS.get(owner.getClass());
            if (!jobAccess.supported() || !ownerAccess.supported()) {
                logAccessFailure(logic.getClass(), "required crafting job or CPU owner fields were not found", null);
                return NOT_HANDLED;
            }
            Map<?, ?> tasks = jobAccess.tasks(job);
            ICraftingInventory waitingFor = jobAccess.waitingFor(job);
            if (tasks == null || waitingFor == null || tasks.isEmpty()) {
                return NOT_HANDLED;
            }

            Iterator<? extends Map.Entry<?, ?>> iterator = tasks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<?, ?> task = iterator.next();
                if (!(task.getKey() instanceof IPatternDetails details) || task.getValue() == null) {
                    continue;
                }
                ProgressAccess progressAccess = PROGRESS_ACCESS.get(task.getValue().getClass());
                if (!progressAccess.supported()) {
                    logAccessFailure(logic.getClass(), "crafting task progress field was not found", null);
                    return NOT_HANDLED;
                }
                long remainingExecutions = progressAccess.get(task.getValue());
                if (remainingExecutions < 2L) {
                    continue;
                }

                ICraftingProvider selectedProvider = null;
                PatternProviderBatchEligibility.BatchTarget batchTarget = null;
                for (ICraftingProvider provider : craftingService.getProviders(details)) {
                    if (provider.isBusy()) {
                        continue;
                    }
                    batchTarget = PatternProviderBatchEligibility.inspect(provider, details, level);
                    if (batchTarget == null) {
                        // Preserve AE2 provider ordering when the first available provider cannot be batched.
                        return NOT_HANDLED;
                    }
                    selectedProvider = provider;
                    break;
                }
                if (selectedProvider == null) {
                    continue;
                }

                long requestedBatch = Math.min(
                        Math.min(remainingExecutions, (long) maxPatterns),
                        (long) ACOConfig.getMaxPatternExecutionsPerMicroBatch());
                long batchSize = safeBatchSize(details, requestedBatch);
                if (batchSize < 2L) {
                    return NOT_HANDLED;
                }

                BatchExtraction extraction = extractBatch(details, inventory, level, batchSize);
                if (extraction == null) {
                    continue;
                }

                double patternPower = CraftingCpuHelper.calculatePatternPower(extraction.inputs());
                if (energyService.extractAEPower(patternPower, Actionable.SIMULATE, PowerMultiplier.CONFIG)
                        < patternPower - 0.01D) {
                    CraftingCpuHelper.reinjectPatternInputs(inventory, extraction.inputs());
                    continue;
                }

                ICraftingProvider provider = selectedProvider;
                PatternProviderBatchEligibility.BatchTarget target = batchTarget;
                boolean pushed = PatternPushContext.withBatch(
                        batchSize,
                        target.providerSide(),
                        () -> provider.pushPattern(details, extraction.inputs()));
                if (!pushed) {
                    CraftingCpuHelper.reinjectPatternInputs(inventory, extraction.inputs());
                    return NOT_HANDLED;
                }
                externalPushAccepted = true;

                energyService.extractAEPower(patternPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
                for (var expectedOutput : extraction.expectedOutputs()) {
                    waitingFor.insert(
                            expectedOutput.getKey(),
                            expectedOutput.getLongValue(),
                            Actionable.MODULATE);
                }

                long remaining = remainingExecutions - batchSize;
                progressAccess.set(task.getValue(), remaining);
                if (remaining <= 0L) {
                    iterator.remove();
                }
                ownerAccess.markDirty(owner);
                OptimizationMetrics.recordPatternMicroBatch(batchSize);
                return Math.toIntExact(batchSize);
            }
            return NOT_HANDLED;
        } catch (Throwable throwable) {
            if (externalPushAccepted) {
                throw new IllegalStateException(
                        "ACO accepted a pattern micro-batch but could not commit AE2 crafting state safely.",
                        throwable);
            }
            logAccessFailure(logic.getClass(), "micro-batch execution fell back to AE2", throwable);
            return NOT_HANDLED;
        }
    }

    private static long safeBatchSize(IPatternDetails details, long requestedBatch) {
        long safe = requestedBatch;
        for (IPatternDetails.IInput input : details.getInputs()) {
            long multiplier = input.getMultiplier();
            if (multiplier <= 0L) {
                return 1L;
            }
            safe = Math.min(safe, Long.MAX_VALUE / multiplier);
            for (var possibleInput : input.getPossibleInputs()) {
                long amount = possibleInput.amount();
                if (amount <= 0L) {
                    return 1L;
                }
                safe = Math.min(safe, Long.MAX_VALUE / multiplier / amount);
            }
        }
        for (var output : details.getOutputs()) {
            if (output.amount() <= 0L) {
                return 1L;
            }
            safe = Math.min(safe, Long.MAX_VALUE / output.amount());
        }
        return safe;
    }

    @Nullable
    private static BatchExtraction extractBatch(
            IPatternDetails details,
            ICraftingInventory inventory,
            Level level,
            long batchSize) {
        IPatternDetails.IInput[] patternInputs = details.getInputs();
        KeyCounter[] extractedInputs = new KeyCounter[patternInputs.length];
        try {
            for (int index = 0; index < patternInputs.length; index++) {
                IPatternDetails.IInput input = patternInputs[index];
                KeyCounter extractedForInput = extractedInputs[index] = new KeyCounter();
                long remainingMultiplier = Math.multiplyExact(input.getMultiplier(), batchSize);

                for (InputTemplate template : CraftingCpuHelper.getValidItemTemplates(inventory, input, level)) {
                    if (remainingMultiplier <= 0L) {
                        break;
                    }
                    if (template.amount() <= 0L || input.getRemainingKey(template.key()) != null) {
                        CraftingCpuHelper.reinjectPatternInputs(inventory, extractedInputs);
                        return null;
                    }

                    long requestedAmount = Math.multiplyExact(template.amount(), remainingMultiplier);
                    long simulated = inventory.extract(template.key(), requestedAmount, Actionable.SIMULATE);
                    long availableTemplates = Math.min(remainingMultiplier, simulated / template.amount());
                    if (availableTemplates <= 0L) {
                        continue;
                    }
                    long amountToExtract = Math.multiplyExact(template.amount(), availableTemplates);
                    long extracted = inventory.extract(template.key(), amountToExtract, Actionable.MODULATE);
                    if (extracted != amountToExtract) {
                        throw new IllegalStateException("AE2 crafting inventory simulation disagreed with extraction");
                    }
                    extractedForInput.add(template.key(), extracted);
                    remainingMultiplier -= availableTemplates;
                }

                if (remainingMultiplier > 0L) {
                    CraftingCpuHelper.reinjectPatternInputs(inventory, extractedInputs);
                    return null;
                }
            }

            KeyCounter expectedOutputs = new KeyCounter();
            for (var output : details.getOutputs()) {
                expectedOutputs.add(output.what(), Math.multiplyExact(output.amount(), batchSize));
            }
            return new BatchExtraction(extractedInputs, expectedOutputs);
        } catch (Throwable throwable) {
            CraftingCpuHelper.reinjectPatternInputs(inventory, extractedInputs);
            throw throwable;
        }
    }

    private static void logAccessFailure(Class<?> logicClass, String message, @Nullable Throwable throwable) {
        String key = logicClass.getName() + ":" + message;
        if (!ACCESS_FAILURES_LOGGED.add(key)) {
            return;
        }
        if (throwable == null) {
            AE2CraftingOptimizer.LOGGER.warn("ACO pattern micro-batching disabled for {}: {}", logicClass.getName(), message);
        } else {
            AE2CraftingOptimizer.LOGGER.warn(
                    "ACO pattern micro-batching disabled for {}: {} ({})",
                    logicClass.getName(),
                    message,
                    throwable.toString());
        }
    }

    private record BatchExtraction(KeyCounter[] inputs, KeyCounter expectedOutputs) {
    }

    private record LogicAccess(
            @Nullable Field jobField,
            @Nullable Field inventoryField,
            @Nullable Field ownerField) {

        static LogicAccess create(Class<?> type) {
            try {
                Field job = findField(type, "job");
                Field inventory = findField(type, "inventory");
                Field owner;
                try {
                    owner = findField(type, "cluster");
                } catch (NoSuchFieldException ignored) {
                    owner = findField(type, "cpu");
                }
                job.setAccessible(true);
                inventory.setAccessible(true);
                owner.setAccessible(true);
                return new LogicAccess(job, inventory, owner);
            } catch (ReflectiveOperationException ignored) {
                return new LogicAccess(null, null, null);
            }
        }

        boolean supported() {
            return jobField != null && inventoryField != null && ownerField != null;
        }

        @Nullable
        Object job(Object logic) throws IllegalAccessException {
            return jobField == null ? null : jobField.get(logic);
        }

        @Nullable
        Object owner(Object logic) throws IllegalAccessException {
            return ownerField == null ? null : ownerField.get(logic);
        }

        @Nullable
        ICraftingInventory inventory(Object logic) throws IllegalAccessException {
            Object value = inventoryField == null ? null : inventoryField.get(logic);
            return value instanceof ICraftingInventory inventory ? inventory : null;
        }
    }

    private record JobAccess(@Nullable Field tasksField, @Nullable Field waitingForField) {
        static JobAccess create(Class<?> type) {
            try {
                Field tasks = findField(type, "tasks");
                Field waitingFor = findField(type, "waitingFor");
                tasks.setAccessible(true);
                waitingFor.setAccessible(true);
                return new JobAccess(tasks, waitingFor);
            } catch (ReflectiveOperationException ignored) {
                return new JobAccess(null, null);
            }
        }

        boolean supported() {
            return tasksField != null && waitingForField != null;
        }

        @Nullable
        Map<?, ?> tasks(Object job) throws IllegalAccessException {
            Object value = tasksField == null ? null : tasksField.get(job);
            return value instanceof Map<?, ?> map ? map : null;
        }

        @Nullable
        ICraftingInventory waitingFor(Object job) throws IllegalAccessException {
            Object value = waitingForField == null ? null : waitingForField.get(job);
            return value instanceof ICraftingInventory inventory ? inventory : null;
        }
    }

    private record ProgressAccess(@Nullable Field valueField) {
        static ProgressAccess create(Class<?> type) {
            try {
                Field value = findField(type, "value");
                value.setAccessible(true);
                return new ProgressAccess(value);
            } catch (ReflectiveOperationException ignored) {
                return new ProgressAccess(null);
            }
        }

        boolean supported() {
            return valueField != null;
        }

        long get(Object progress) throws IllegalAccessException {
            return valueField == null ? 0L : valueField.getLong(progress);
        }

        void set(Object progress, long value) throws IllegalAccessException {
            if (valueField != null) {
                valueField.setLong(progress, value);
            }
        }
    }

    private record OwnerAccess(@Nullable Method markDirtyMethod) {
        static OwnerAccess create(Class<?> type) {
            try {
                Method markDirty = findMethod(type, "markDirty");
                markDirty.setAccessible(true);
                return new OwnerAccess(markDirty);
            } catch (ReflectiveOperationException ignored) {
                return new OwnerAccess(null);
            }
        }

        boolean supported() {
            return markDirtyMethod != null;
        }

        void markDirty(Object owner) throws ReflectiveOperationException {
            if (markDirtyMethod != null) {
                markDirtyMethod.invoke(owner);
            }
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Method findMethod(Class<?> type, String name) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return type.getMethod(name);
    }
}

package com.syaru.ae2craftingoptimizer.api.batch;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.world.level.Level;
import com.syaru.ae2craftingoptimizer.access.ExactCraftingInventoryAccess;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.CountedInputPreview;
import com.syaru.ae2craftingoptimizer.optimization.GTCountedInputPush;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

/** Synchronous counted transfer. The caller retains exact order and CPU job accounting. */
public final class CountedProcessingTransfers {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final Result EMPTY = new Result(0, Map.of());

    private CountedProcessingTransfers() {}

    public static int apiVersion() { return 1; }

    public static boolean enabled() {
        return ACOConfig.enableCountedProcessingTransfers();
    }

    /**
     * Null declines BEFORE extraction; zero is a supported but currently blocked route.
     * After provider acceptance no failure is caught, refunded or retried by this API.
     */
    public static Result transfer(IPatternDetails pattern, long remaining, int logicalBudget,
            ICraftingInventory source, ICraftingInventory waiting,
            ICraftingProvider provider, IEnergyService energy) {
        return transfer(pattern, BigInteger.valueOf(remaining), logicalBudget, source, waiting,
                provider, energy, null);
    }

    /** The order stays exact; only this CPU grant and native delivery use bounded quantities. */
    public static Result transfer(IPatternDetails pattern, BigInteger remaining, int logicalBudget,
            ICraftingInventory source, ICraftingInventory waiting,
            ICraftingProvider provider, IEnergyService energy, Level level) {
        if (!enabled() || pattern == null
                || !(provider instanceof CountedPatternBatchTarget target)) return null;
        if (logicalBudget <= 0 || remaining.signum() <= 0 || provider.isBusy()) return EMPTY;
        boolean supported = target.aco$supportsCountedPattern(pattern);
        boolean nativePreview = pattern.getClass() == AEProcessingPattern.class
                && source.getClass() == ListCraftingInventory.class
                && (!(source instanceof ExactCraftingInventoryAccess access) || !access.aco$hasExactCounts());
        if (!supported && !nativePreview) return null;
        var inputs = pattern.getInputs();
        if (inputs.length == 0) return null;
        long offered = remaining.min(BigInteger.valueOf(logicalBudget)).longValueExact();
        var exact = nativePreview ? previewNativeInputs(inputs, source, offered, supported) : null;
        if (exact == NativePreview.BLOCKED) return EMPTY;
        if (!supported) return null;
        Map<AEKey, BigInteger> required;
        KeyCounter[] perSlot;
        if (exact != null) {
            offered = exact.limit();
            required = exact.required();
            perSlot = exact.slots();
        } else {
            for (var input : inputs) {
                var alternatives = input.getPossibleInputs();
                long multiplier = input.getMultiplier();
                if (alternatives.length == 0 || multiplier <= 0) return null;
                for (var alternative : alternatives) {
                    if (alternative == null || alternative.amount() <= 0
                            || input.getRemainingKey(alternative.what()) != null
                            || alternative.amount() > Long.MAX_VALUE / multiplier) return null;
                }
            }
            var containers = new KeyCounter();
            perSlot = CraftingCpuHelper.extractPatternInputs(pattern, new CountedInputPreview(source),
                    level, new KeyCounter(), containers);
            if (perSlot == null) return EMPTY;
            if (!containers.isEmpty()) return null;
            required = new LinkedHashMap<>();
            for (var slot : perSlot) {
                for (var input : slot) {
                    if (input.getLongValue() < 0) return null;
                    if (input.getLongValue() > 0)
                        required.merge(input.getKey(), BigInteger.valueOf(input.getLongValue()), BigInteger::add);
                }
            }
            if (required.isEmpty()) return null;
        }
        Map<AEKey, BigInteger> produced = new LinkedHashMap<>();
        for (var output : pattern.getOutputs()) {
            if (output == null || output.amount() <= 0) return null;
            produced.merge(output.what(), BigInteger.valueOf(output.amount()), BigInteger::add);
        }
        if (produced.isEmpty()) return null;
        if (exact == null) {
            for (var entry : required.entrySet()) {
                long available = source.extract(entry.getKey(), Long.MAX_VALUE, Actionable.SIMULATE);
                if (available < 0) throw new IllegalStateException("negative native CPU stock");
                offered = bounded(offered, BigInteger.valueOf(available).divide(entry.getValue()));
            }
        }
        for (var entry : produced.entrySet()) {
            long pending = waiting.extract(entry.getKey(), Long.MAX_VALUE, Actionable.SIMULATE);
            if (pending < 0) throw new IllegalStateException("negative native waiting count");
            offered = bounded(offered, LONG_MAX.subtract(BigInteger.valueOf(pending)).divide(entry.getValue()));
        }
        var requiredView = Collections.unmodifiableMap(required);
        long targetLimit = target.aco$countedExecutionLimit(pattern, requiredView, offered);
        if (targetLimit == -1) return null;
        if (targetLimit < 0 || targetLimit > offered) throw new IllegalStateException("invalid counted target limit");
        offered = targetLimit;
        if (offered == 0) return EMPTY;
        var payload = scale(perSlot, offered);
        double power = CraftingCpuHelper.calculatePatternPower(payload);
        if (!Double.isFinite(power) || power <= 0) return null;
        double availablePower = energy.extractAEPower(power, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (Double.isNaN(availablePower) || availablePower < 0) return EMPTY;
        if (availablePower < power - 0.01) {
            double perExecution = CraftingCpuHelper.calculatePatternPower(perSlot);
            if (!Double.isFinite(perExecution) || perExecution <= 0) return null;
            long powered = (long) Math.floor(Math.max(0, availablePower) / perExecution);
            offered = Math.min(offered, powered);
            if (offered <= 0) return EMPTY;
            payload = scale(perSlot, offered);
            power = CraftingCpuHelper.calculatePatternPower(payload);
            double simulated = energy.extractAEPower(power, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            if (Double.isNaN(simulated) || simulated < 0) return EMPTY;
            if (simulated < power - 0.01) {
                if (offered <= 1) return EMPTY;
                offered--;
                payload = scale(perSlot, offered);
                power = CraftingCpuHelper.calculatePatternPower(payload);
                if (!(energy.extractAEPower(power, Actionable.SIMULATE, PowerMultiplier.CONFIG) >= power - 0.01)) return EMPTY;
            }
        }
        var extracted = new KeyCounter[payload.length];
        for (int i = 0; i < payload.length; i++) {
            extracted[i] = new KeyCounter();
            for (var entry : payload[i]) {
                long actual = source.extract(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
                if (actual < 0 || actual > entry.getLongValue()) throw new IllegalStateException("invalid native extraction");
                extracted[i].add(entry.getKey(), actual);
                if (actual != entry.getLongValue()) {
                    CraftingCpuHelper.reinjectPatternInputs(source, extracted);
                    return EMPTY;
                }
            }
        }
        // #242: new admission callbacks cannot strand inputs before provider acceptance.
        boolean stillValid = true;
        try {
            for (int i = 0; i < extracted.length; i++) {
                for (var entry : extracted[i]) {
                    if (inputs[i].getRemainingKey(entry.getKey()) != null
                            || (level != null && !inputs[i].isValid(entry.getKey(), level))) {
                        stillValid = false;
                    }
                }
            }
            stillValid &= !provider.isBusy()
                    && target.aco$countedExecutionLimit(pattern, requiredView, offered) >= offered
                    && energy.extractAEPower(power, Actionable.SIMULATE, PowerMultiplier.CONFIG) >= power - 0.01;
        } catch (RuntimeException | LinkageError failure) {
            CraftingCpuHelper.reinjectPatternInputs(source, extracted);
            throw failure;
        }
        if (!stillValid) {
            CraftingCpuHelper.reinjectPatternInputs(source, extracted);
            return EMPTY;
        }
        // Issue #161: the real provider still performs formed/active/pattern/input acceptance.
        if (!GTCountedInputPush.push(provider, pattern, extracted)) {
            CraftingCpuHelper.reinjectPatternInputs(source, extracted);
            return EMPTY;
        }
        // Same native ordering: acceptance, then actual ME power. No fallback after this point.
        energy.extractAEPower(power, Actionable.MODULATE, PowerMultiplier.CONFIG);
        Map<AEKey, BigInteger> outputs = new LinkedHashMap<>();
        var count = BigInteger.valueOf(offered);
        produced.forEach((key, amount) -> outputs.put(key, amount.multiply(count)));
        return new Result(Math.toIntExact(offered), outputs);
    }

    private static NativePreview previewNativeInputs(IPatternDetails.IInput[] inputs, ICraftingInventory source,
            long offered, boolean buildPayload) {
        // AE2's native processing slots use equality, have no remainders, and are already condensed.
        Map<AEKey, BigInteger> required = new LinkedHashMap<>();
        for (var input : inputs) {
            var alternatives = input.getPossibleInputs();
            long multiplier = input.getMultiplier();
            if (alternatives.length != 1 || multiplier <= 0) return null;
            var stack = alternatives[0];
            if (stack == null || stack.amount() != 1) return null;
            var key = stack.what();
            if (key.getClass() != AEItemKey.class && key.getClass() != AEFluidKey.class) return null;
            if (required.putIfAbsent(key, BigInteger.valueOf(multiplier)) != null) return null;
        }
        for (var entry : required.entrySet()) {
            long available = source.extract(entry.getKey(), Long.MAX_VALUE, Actionable.SIMULATE);
            if (available < 0) throw new IllegalStateException("negative native CPU stock");
            offered = Math.min(offered, available / entry.getValue().longValueExact());
            if (offered == 0) return NativePreview.BLOCKED;
        }
        // A native-only route uses the stock proof to skip blocked work, never a second payload.
        if (!buildPayload) return null;
        var slots = new KeyCounter[inputs.length];
        int slot = 0;
        for (var entry : required.entrySet()) {
            var counter = new KeyCounter();
            counter.add(entry.getKey(), entry.getValue().longValueExact());
            slots[slot++] = counter;
        }
        return new NativePreview(slots, required, offered);
    }

    private record NativePreview(KeyCounter[] slots, Map<AEKey, BigInteger> required, long limit) {
        private static final NativePreview BLOCKED = new NativePreview(null, Map.of(), 0);
    }

    private static long bounded(long offered, BigInteger maximum) {
        return BigInteger.valueOf(offered).min(maximum).longValueExact();
    }

    private static KeyCounter[] scale(KeyCounter[] inputs, long count) {
        var result = new KeyCounter[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            result[i] = new KeyCounter();
            for (var entry : inputs[i]) {
                result[i].add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), count));
            }
        }
        return result;
    }

    public record Result(int executions, Map<AEKey, BigInteger> expectedOutputs) {
        public Result {
            if (executions < 0) throw new IllegalArgumentException("negative counted execution");
            expectedOutputs = Map.copyOf(expectedOutputs);
        }
    }
}

package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.BigCapacityCraftingPlan;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;

/** Exact capacity validation for plans whose quantities already fit native AE2 execution. */
public final class CapacityOnlySubmission {
    private CapacityOnlySubmission() {
    }

    public static BigInteger availableCapacity(Object cpu, long nativeCapacity) {
        Objects.requireNonNull(cpu, "cpu");
        // Optional add-ons expose these read-only contracts; never alter their capacity state.
        for (String getter : new String[] {"ccb$getExactStorageCapacity", "insaneae$exactStorageCapacity"}) {
            try {
                Object result = cpu.getClass().getMethod(getter).invoke(cpu);
                if (!(result instanceof BigInteger exact) || exact.signum() < 0) {
                    throw new IllegalStateException("Invalid exact CPU capacity from " + getter);
                }
                return exact;
            } catch (NoSuchMethodException absent) {
                // An absent optional contract is different from a broken capacity provider.
            } catch (IllegalAccessException | InvocationTargetException failure) {
                throw new IllegalStateException("Cannot read exact CPU capacity from " + getter, failure);
            }
        }
        if (nativeCapacity < 0) {
            throw new IllegalStateException("Negative native CPU capacity");
        }
        return BigInteger.valueOf(nativeCapacity);
    }

    public static CraftingPlan executionPlan(BigCapacityCraftingPlan plan, BigInteger capacity) {
        if (capacity.signum() < 0 || plan.exactBytes().compareTo(capacity) > 0) {
            throw new IllegalArgumentException("Insufficient exact CPU capacity");
        }
        if (plan.simulation() || plan.finalOutput().amount() <= 0) {
            throw new IllegalArgumentException("Incomplete capacity-only plan");
        }
        for (var entry : plan.missingItems()) {
            if (entry.getLongValue() != 0) {
                throw new IllegalArgumentException("Capacity-only plan still has missing items");
            }
        }
        for (long count : plan.patternTimes().values()) {
            if (count <= 0) {
                throw new IllegalArgumentException("Invalid native pattern count");
            }
        }
        // Only this private execution copy omits the sidecar; the public original remains exact.
        return new CraftingPlan(plan.finalOutput(), plan.bytes(), false, plan.multiplePaths(),
                copy(plan.usedItems()), copy(plan.emittedItems()), new KeyCounter(),
                Map.copyOf(plan.patternTimes()));
    }

    private static KeyCounter copy(KeyCounter source) {
        var copy = new KeyCounter();
        for (var entry : source) {
            if (entry.getLongValue() < 0) {
                throw new IllegalArgumentException("Negative native item count");
            }
            copy.add(entry.getKey(), entry.getLongValue());
        }
        return copy;
    }
}

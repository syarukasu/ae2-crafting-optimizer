package com.syaru.ae2craftingoptimizer.optimization;

import java.util.function.BooleanSupplier;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public final class PatternPushContext {
    private static final Context SINGLE_EXECUTION = new Context(1L, null);
    private static final ThreadLocal<Context> CURRENT = ThreadLocal.withInitial(() -> SINGLE_EXECUTION);

    private PatternPushContext() {
    }

    public static long patternExecutions() {
        return CURRENT.get().patternExecutions();
    }

    @Nullable
    public static Direction providerSide() {
        return CURRENT.get().providerSide();
    }

    public static boolean withBatch(long patternExecutions, @Nullable Direction providerSide, BooleanSupplier action) {
        Context previous = CURRENT.get();
        CURRENT.set(new Context(Math.max(1L, patternExecutions), providerSide));
        try {
            return action.getAsBoolean();
        } finally {
            if (previous == SINGLE_EXECUTION) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    private record Context(long patternExecutions, @Nullable Direction providerSide) {
    }
}

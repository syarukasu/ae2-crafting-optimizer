package com.syaru.ae2craftingoptimizer.batch;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext;
import com.syaru.ae2craftingoptimizer.util.StableFingerprint;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

public final class NativePatternBatchSupport {
    private NativePatternBatchSupport() {
    }

    public static KeyCounter[] scaleInputs(PatternBatchContext context, long executions) {
        KeyCounter[] source = context.copyInputsPerExecution();
        KeyCounter[] scaled = new KeyCounter[source.length];
        for (int index = 0; index < source.length; index++) {
            KeyCounter counter = scaled[index] = new KeyCounter();
            for (var entry : source[index]) {
                counter.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), executions));
            }
        }
        return scaled;
    }

    public static List<GenericStack> flatten(KeyCounter[] counters) {
        List<GenericStack> result = new ArrayList<>();
        for (KeyCounter counter : counters) {
            for (var entry : counter) {
                result.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }
        return List.copyOf(result);
    }

    public static List<GenericStack> scaleOutputs(PatternBatchContext context, long executions) {
        List<GenericStack> result = new ArrayList<>();
        for (var entry : context.copyOutputsPerExecution()) {
            result.add(new GenericStack(
                    entry.getKey(), Math.multiplyExact(entry.getLongValue(), executions)));
        }
        return List.copyOf(result);
    }

    public static long safeExecutionLimit(PatternBatchContext context, long offered) {
        long safe = offered;
        for (KeyCounter counter : context.copyInputsPerExecution()) {
            for (var entry : counter) {
                safe = Math.min(safe, Long.MAX_VALUE / entry.getLongValue());
            }
        }
        for (var entry : context.copyOutputsPerExecution()) {
            safe = Math.min(safe, Long.MAX_VALUE / entry.getLongValue());
        }
        return safe;
    }

    public static String fingerprint(PatternBatchContext context) {
        StringBuilder value = new StringBuilder(256);
        value.append(PatternTaskFingerprint.of(context.pattern()));
        for (KeyCounter counter : context.copyInputsPerExecution()) {
            value.append("|i");
            for (var entry : counter) {
                value.append(':')
                        .append(entry.getKey().toTagGeneric())
                        .append('@')
                        .append(entry.getLongValue());
            }
        }
        for (var entry : context.copyOutputsPerExecution()) {
            value.append("|o:")
                    .append(entry.getKey().toTagGeneric())
                    .append('@')
                    .append(entry.getLongValue());
        }
        return StableFingerprint.sha256(value) + ':' + context.pattern().getDefinition().getId();
    }

    public static CompoundTag targetMetadata(PatternBatchContext context) {
        CompoundTag data = new CompoundTag();
        data.putInt("schema", 2);
        data.putLong("providerPos", context.target().getBlockPos().relative(context.targetSide()).asLong());
        data.putLong("targetPos", context.target().getBlockPos().asLong());
        data.putByte("providerSide", (byte) context.providerSide().get3DDataValue());
        data.putByte("targetSide", (byte) context.targetSide().get3DDataValue());
        return data;
    }
}

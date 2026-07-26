package com.syaru.ae2craftingoptimizer.engine.vector;

import com.syaru.ae2craftingoptimizer.api.vector.ExactStack;
import com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatch;
import java.math.BigInteger;
import java.util.Objects;

/** Config上限とBigInteger桁数を、Executor選択より前に一か所で検査する。 */
public final class VectorBatchPlanValidator {
    private VectorBatchPlanValidator() {
    }

    public static void validate(
            PreparedVectorBatch plan,
            int maximumBits,
            int maximumPatternNodes,
            int maximumInputKeys,
            int maximumOutputKeys) {
        Objects.requireNonNull(plan, "plan");
        if (maximumBits < Long.SIZE
                || maximumPatternNodes <= 0
                || maximumInputKeys <= 0
                || maximumOutputKeys <= 0) {
            throw new IllegalArgumentException("invalid vector validation limits");
        }
        if (plan.requiredPatternIds().size() > maximumPatternNodes
                || plan.totalInputs().size() > maximumInputKeys
                || Math.addExact(
                                plan.finalOutputs().size(),
                                plan.remainingOutputs().size())
                        > maximumOutputKeys) {
            throw new IllegalArgumentException(
                    "vector plan exceeds configured key or pattern limits");
        }
        requireBits(plan.requestedAmount(), maximumBits, "requestedAmount");
        requireBits(plan.logicalExecutions(), maximumBits, "logicalExecutions");
        requireBits(plan.totalEnergyMicroAe(), maximumBits, "totalEnergyMicroAe");
        requireBits(plan.totalCoolant(), maximumBits, "totalCoolant");
        checkStacks(plan.totalInputs(), maximumBits, "input");
        checkStacks(plan.finalOutputs(), maximumBits, "final output");
        checkStacks(plan.remainingOutputs(), maximumBits, "remaining output");
    }

    private static void checkStacks(
            Iterable<ExactStack> stacks,
            int maximumBits,
            String name) {
        // キー数は上位で制限済みなので、各値を一度だけ検証する。
        for (ExactStack stack : stacks) {
            requireBits(stack.amount(), maximumBits, name);
        }
    }

    private static void requireBits(
            BigInteger value,
            int maximumBits,
            String name) {
        if (value.signum() < 0 || value.bitLength() > maximumBits) {
            throw new ArithmeticException(
                    name + " exceeds the Exact Vector magnitude limit");
        }
    }
}

package com.syaru.ae2craftingoptimizer.engine.vector;

import com.syaru.ae2craftingoptimizer.api.vector.ExactStack;
import com.syaru.ae2craftingoptimizer.util.StableFingerprint;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/** Programと正確な境界量から、保存Receiptを取り違えない安定SHA-256を作る。 */
public final class VectorPlanFingerprint {
    private VectorPlanFingerprint() {
    }

    public static String create(
            String programFingerprint,
            BigInteger requestedAmount,
            List<ExactStack> inputs,
            List<ExactStack> outputs) {
        StringBuilder source = new StringBuilder(
                Objects.requireNonNull(programFingerprint, "programFingerprint"));
        source.append("|requested=").append(
                Objects.requireNonNull(requestedAmount, "requestedAmount"));
        appendStacks(source, "in", inputs);
        appendStacks(source, "out", outputs);
        return StableFingerprint.sha256(source);
    }

    private static void appendStacks(
            StringBuilder target,
            String prefix,
            List<ExactStack> stacks) {
        // Plannerが安定ノード順で作った一覧を維持し、量を10進文字列で無損失に含める。
        for (ExactStack stack : stacks) {
            target.append('|')
                    .append(prefix)
                    .append(':')
                    .append(stack.key().toTagGeneric())
                    .append('@')
                    .append(stack.amount());
        }
    }
}

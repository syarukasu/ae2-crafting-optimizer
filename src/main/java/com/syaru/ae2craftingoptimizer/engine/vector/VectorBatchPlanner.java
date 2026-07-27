package com.syaru.ae2craftingoptimizer.engine.vector;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.api.vector.ExactStack;
import com.syaru.ae2craftingoptimizer.api.vector.ExactCraftingInputSlot;
import com.syaru.ae2craftingoptimizer.api.vector.ExactCraftingStep;
import com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatch;
import com.syaru.ae2craftingoptimizer.api.vector.VectorResourceMode;
import com.syaru.ae2craftingoptimizer.engine.CompiledRootProgram;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Compiled Root Programを、要求数量に依存しない一つのExact Vector式へ変換する。 */
public final class VectorBatchPlanner {
    private VectorBatchPlanner() {
    }

    public static PreparedVectorBatch prepare(
            UUID transactionId,
            UUID parentJobId,
            CompiledRootProgram<AEKey> program,
            CompiledRootProgram.BigInventorySnapshot<AEKey> inventory,
            BigInteger requestedAmount,
            String programFingerprint,
            long patternGeneration,
            long recipeGeneration,
            int maximumBits) {
        return prepare(
                transactionId,
                parentJobId,
                program,
                inventory,
                requestedAmount,
                programFingerprint,
                patternGeneration,
                recipeGeneration,
                maximumBits,
                VectorPlanFingerprint::create);
    }

    static PreparedVectorBatch prepare(
            UUID transactionId,
            UUID parentJobId,
            CompiledRootProgram<AEKey> program,
            CompiledRootProgram.BigInventorySnapshot<AEKey> inventory,
            BigInteger requestedAmount,
            String programFingerprint,
            long patternGeneration,
            long recipeGeneration,
            int maximumBits,
            FingerprintFactory fingerprintFactory) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(requestedAmount, "requestedAmount");
        Objects.requireNonNull(
                fingerprintFactory,
                "fingerprintFactory");

        CompiledRootProgram.DeterministicCraftingBigPlan<AEKey> deterministic =
                program.tryPlanDeterministicCraftingBig(
                                requestedAmount,
                                inventory,
                                maximumBits)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "vector plan is not a deterministic crafting DAG"));
        // 不足または実行Patternなしの計画を設備へ渡さない。
        if (!deterministic.craftable()
                || deterministic.requiredPatternIds().isEmpty()) {
            throw new IllegalArgumentException(
                    "vector plan is missing or contains no crafting work");
        }

        Map<AEKey, BigInteger> boundaryInputs =
                deterministic.boundaryInputs();
        Map<AEKey, BigInteger> boundaryOutputs =
                deterministic.boundaryOutputs();
        BigInteger rootBoundary = boundaryOutputs.getOrDefault(
                program.root(), BigInteger.ZERO);
        // 最終成果物が要求量を満たさない数式は実行設備へ渡さない。
        if (rootBoundary.compareTo(requestedAmount) < 0) {
            throw new IllegalArgumentException(
                    "vector boundary output does not satisfy its root request");
        }

        List<ExactStack> finalOutputs =
                List.of(new ExactStack(program.root(), requestedAmount));
        Map<AEKey, BigInteger> remaining = new LinkedHashMap<>(boundaryOutputs);
        BigInteger rootRemainder = rootBoundary.subtract(requestedAmount);
        // 最終成果物の過剰分だけを余剰出力へ残し、0 entryは保存しない。
        if (rootRemainder.signum() == 0) {
            remaining.remove(program.root());
        } else {
            remaining.put(program.root(), rootRemainder);
        }

        int stages = deterministic.logicalStageCount();
        // 実行Patternがあるのに0段の計画は内部不整合なので実行しない。
        if (stages <= 0) {
            throw new IllegalArgumentException(
                    "deterministic vector plan has no logical stages");
        }
        List<ExactStack> inputs = exactStacks(boundaryInputs);
        List<ExactStack> remainingOutputs = exactStacks(remaining);
        List<ExactCraftingStep> craftingSteps =
                deterministic.patternSteps().stream()
                        .map(step -> new ExactCraftingStep(
                                step.patternId(),
                                step.depth(),
                                step.executions(),
                                step.selectedInputs()
                                        .stream()
                                        .map(input ->
                                                new ExactCraftingInputSlot(
                                                        input.key(),
                                                        input.amount()))
                                        .toList()))
                        .toList();
        String transactionFingerprint = fingerprintFactory.create(
                programFingerprint,
                requestedAmount,
                inputs,
                mergeOutputs(finalOutputs, remainingOutputs));

        return new PreparedVectorBatch(
                transactionId,
                parentJobId,
                VectorResourceMode.NETWORK_STORAGE,
                program.root(),
                requestedAmount,
                deterministic.logicalExecutions(),
                stages,
                inputs,
                finalOutputs,
                remainingOutputs,
                deterministic.requiredPatternIds(),
                craftingSteps,
                transactionFingerprint,
                patternGeneration,
                recipeGeneration);
    }

    /**
     * 数式計画とAEKey直列化を分離する内部境界。
     *
     * <p>通常経路は常に{@link VectorPlanFingerprint#create}を渡す。単体試験だけが
     * Minecraft Registryを起動せず、数量会計を独立検証するために差し替える。</p>
     */
    @FunctionalInterface
    interface FingerprintFactory {
        String create(
                String programFingerprint,
                BigInteger requestedAmount,
                List<ExactStack> inputs,
                List<ExactStack> outputs);
    }

    private static List<ExactStack> exactStacks(
            Map<AEKey, BigInteger> counts) {
        List<ExactStack> result = new ArrayList<>(counts.size());
        // LinkedHashMapの安定ノード順を保存Receiptでも維持する。
        for (Map.Entry<AEKey, BigInteger> entry : counts.entrySet()) {
            // 相殺後に残った正数の境界だけを設備へ渡す。
            if (entry.getValue().signum() > 0) {
                result.add(new ExactStack(entry.getKey(), entry.getValue()));
            }
        }
        return List.copyOf(result);
    }

    private static List<ExactStack> mergeOutputs(
            List<ExactStack> finalOutputs,
            List<ExactStack> remainingOutputs) {
        Map<AEKey, BigInteger> merged = new LinkedHashMap<>();
        // Fingerprint用だけは最終出力と余剰出力の同一キーを合算する。
        for (ExactStack stack : finalOutputs) {
            merged.merge(stack.key(), stack.amount(), BigInteger::add);
        }
        // 余剰出力も同じAEKeyへ合算し、順序に依存しないFingerprintを作る。
        for (ExactStack stack : remainingOutputs) {
            merged.merge(stack.key(), stack.amount(), BigInteger::add);
        }
        return exactStacks(merged);
    }

}

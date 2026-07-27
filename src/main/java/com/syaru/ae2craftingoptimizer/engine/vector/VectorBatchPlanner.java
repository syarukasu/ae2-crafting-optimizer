package com.syaru.ae2craftingoptimizer.engine.vector;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.api.vector.ExactStack;
import com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatch;
import com.syaru.ae2craftingoptimizer.api.vector.VectorResourceMode;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.CompiledPattern;
import com.syaru.ae2craftingoptimizer.engine.CompiledRootProgram;
import com.syaru.ae2craftingoptimizer.engine.PlanningGuard;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
            int ticksPerLogicalStage,
            BigInteger energyMicroAePerPatternNode,
            BigInteger totalCoolant,
            String programFingerprint,
            long patternGeneration,
            long recipeGeneration,
            int maximumBits) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(requestedAmount, "requestedAmount");
        Objects.requireNonNull(
                energyMicroAePerPatternNode,
                "energyMicroAePerPatternNode");
        // 時間は正数、設備資源は非負数だけを数式計画へ入れる。
        if (ticksPerLogicalStage <= 0
                || energyMicroAePerPatternNode.signum() < 0
                || totalCoolant.signum() < 0) {
            throw new IllegalArgumentException("invalid vector timing or resource cost");
        }

        BigCraftingPlan<AEKey> symbolic = program.planBig(
                requestedAmount,
                inventory,
                PlanningGuard.none(),
                maximumBits);
        // 不足、Emitter、実行Patternなしの計画を設備へ渡さない。
        if (!symbolic.craftable()
                || !symbolic.emitted().isEmpty()
                || symbolic.patternExecutions().isEmpty()) {
            throw new IllegalArgumentException(
                    "vector plan is missing, emitted, or contains no crafting work");
        }

        Map<AEKey, BigInteger> produced = new LinkedHashMap<>();
        Map<AEKey, BigInteger> consumed = new LinkedHashMap<>();
        Set<String> requiredPatterns = new LinkedHashSet<>();
        BigInteger logicalExecutions = BigInteger.ZERO;

        // 各固有Patternノードを一度だけ読み、数量はBigInteger乗算だけで合算する。
        for (int node = 0; node < program.nodeCount(); node++) {
            String patternId = program.patternIdAt(node);
            BigInteger executions = patternId == null
                    ? BigInteger.ZERO
                    : symbolic.patternExecutions()
                            .getOrDefault(patternId, BigInteger.ZERO);
            // 実行0の在庫終端や不要分岐はVector式へ含めない。
            if (executions.signum() == 0) {
                continue;
            }
            CompiledPattern<AEKey> pattern = program.patternAt(node);
            // Processing Patternは外部機械時間を消せないため初期APIでは拒否する。
            if (pattern == null || pattern.externalPush()) {
                throw new IllegalArgumentException(
                        "vector plan contains a processing or unresolved pattern");
            }
            requiredPatterns.add(patternId);
            logicalExecutions = checkedAdd(
                    logicalExecutions,
                    executions,
                    maximumBits,
                    "logical executions");

            BigInteger outputAmount = checkedMultiply(
                    BigInteger.valueOf(program.outputAmountAt(node)),
                    executions,
                    maximumBits,
                    "pattern output");
            merge(
                    produced,
                    program.keyAt(node),
                    outputAmount,
                    maximumBits,
                    "produced");

            // 同じ入力キーが複数slotにある場合も一つのBigInteger差分へ集約する。
            for (int input = 0; input < program.inputCountAt(node); input++) {
                BigInteger amount = checkedMultiply(
                        BigInteger.valueOf(program.inputAmountAt(node, input)),
                        executions,
                        maximumBits,
                        "pattern input");
                merge(
                        consumed,
                        program.inputKeyAt(node, input),
                        amount,
                        maximumBits,
                        "consumed");
            }
        }

        Map<AEKey, BigInteger> boundaryInputs = new LinkedHashMap<>();
        Map<AEKey, BigInteger> boundaryOutputs = new LinkedHashMap<>();
        Set<AEKey> allKeys = new LinkedHashSet<>(produced.keySet());
        allKeys.addAll(consumed.keySet());
        // 中間素材の生産と消費を相殺し、外部へ触れるキーだけを残す。
        for (AEKey key : allKeys) {
            BigInteger producedAmount =
                    produced.getOrDefault(key, BigInteger.ZERO);
            BigInteger consumedAmount =
                    consumed.getOrDefault(key, BigInteger.ZERO);
            int comparison = producedAmount.compareTo(consumedAmount);
            // 消費超過は外部入力、生産超過は外部出力として一度だけ残す。
            if (comparison < 0) {
                boundaryInputs.put(key, consumedAmount.subtract(producedAmount));
            } else if (comparison > 0) {
                boundaryOutputs.put(key, producedAmount.subtract(consumedAmount));
            }
        }

        // 数式相殺結果とPlannerが実際に予約した在庫が一致しない経路は採用しない。
        if (!boundaryInputs.equals(symbolic.usedInventory())) {
            throw new IllegalArgumentException(
                    "vector boundary inputs differ from the authoritative inventory plan");
        }
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

        int stages = VectorCriticalPathCalculator.calculate(
                program, requiredPatterns);
        int durationTicks = Math.multiplyExact(stages, ticksPerLogicalStage);
        /*
         * 数量は数式上の係数であり、物理的なPattern処理回数ではない。
         * 電力も固有Patternノード数だけで決め、巨大注文を線形コストへ戻さない。
         */
        BigInteger totalEnergy = VectorEnergyCost.forPatternNodes(
                requiredPatterns.size(),
                energyMicroAePerPatternNode,
                maximumBits);
        List<ExactStack> inputs = exactStacks(boundaryInputs);
        List<ExactStack> remainingOutputs = exactStacks(remaining);
        String transactionFingerprint = VectorPlanFingerprint.create(
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
                logicalExecutions,
                stages,
                durationTicks,
                inputs,
                finalOutputs,
                remainingOutputs,
                List.copyOf(requiredPatterns),
                totalEnergy,
                totalCoolant,
                transactionFingerprint,
                patternGeneration,
                recipeGeneration);
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

    private static void merge(
            Map<AEKey, BigInteger> target,
            AEKey key,
            BigInteger amount,
            int maximumBits,
            String name) {
        BigInteger next = checkedAdd(
                target.getOrDefault(key, BigInteger.ZERO),
                amount,
                maximumBits,
                name);
        target.put(key, next);
    }

    private static BigInteger checkedAdd(
            BigInteger left,
            BigInteger right,
            int maximumBits,
            String name) {
        BigInteger result = left.add(right);
        return checked(result, maximumBits, name);
    }

    private static BigInteger checkedMultiply(
            BigInteger left,
            BigInteger right,
            int maximumBits,
            String name) {
        BigInteger result = left.multiply(right);
        return checked(result, maximumBits, name);
    }

    private static BigInteger checked(
            BigInteger value,
            int maximumBits,
            String name) {
        // 全加算・乗算結果を同じ符号とbit上限で検査し、暗黙クランプを許さない。
        if (value.signum() < 0 || value.bitLength() > maximumBits) {
            throw new ArithmeticException(name + " exceeds maximumBits");
        }
        return value;
    }
}

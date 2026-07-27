package com.syaru.ae2craftingoptimizer.api.vector;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 計算スレッドで完成し、実行中に再びグラフ探索しない不変Vector計画。 */
public record PreparedVectorBatch(
        UUID transactionId,
        UUID parentJobId,
        VectorResourceMode resourceMode,
        AEKey requestedOutput,
        BigInteger requestedAmount,
        BigInteger logicalExecutions,
        int logicalStageCount,
        int durationTicks,
        List<ExactStack> totalInputs,
        List<ExactStack> finalOutputs,
        List<ExactStack> remainingOutputs,
        List<String> requiredPatternIds,
        BigInteger totalEnergyMicroAe,
        BigInteger totalCoolant,
        String programFingerprint,
        long patternGeneration,
        long recipeGeneration) {
    public PreparedVectorBatch {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(parentJobId, "parentJobId");
        Objects.requireNonNull(resourceMode, "resourceMode");
        Objects.requireNonNull(requestedOutput, "requestedOutput");
        requestedAmount = requirePositive(requestedAmount, "requestedAmount");
        logicalExecutions = requirePositive(logicalExecutions, "logicalExecutions");
        // 一段以上の決定的クラフトだけをVector実行へ渡す。
        if (logicalStageCount <= 0 || durationTicks < logicalStageCount) {
            throw new IllegalArgumentException("invalid vector stage duration");
        }
        totalInputs = checkedStacks(totalInputs, "totalInputs");
        finalOutputs = checkedStacks(finalOutputs, "finalOutputs");
        remainingOutputs = checkedStacks(remainingOutputs, "remainingOutputs");
        requiredPatternIds = checkedPatternIds(requiredPatternIds);
        totalEnergyMicroAe = requireNonNegative(
                totalEnergyMicroAe, "totalEnergyMicroAe");
        totalCoolant = requireNonNegative(totalCoolant, "totalCoolant");
        programFingerprint = Objects.requireNonNull(
                        programFingerprint, "programFingerprint")
                .trim();
        // 空Fingerprintでは再起動後に同じProgramかを証明できない。
        if (programFingerprint.isEmpty()) {
            throw new IllegalArgumentException("programFingerprint must not be blank");
        }
        if (patternGeneration < 0L || recipeGeneration < 0L) {
            throw new IllegalArgumentException(
                    "vector plan generations must not be negative");
        }

        BigInteger finalRequested = BigInteger.ZERO;
        // 最終出力一覧が親Jobへ約束した要求量を正確に含むことを確認する。
        for (ExactStack output : finalOutputs) {
            if (output.key().equals(requestedOutput)) {
                finalRequested = finalRequested.add(output.amount());
            }
        }
        if (!finalRequested.equals(requestedAmount)) {
            throw new IllegalArgumentException(
                    "final outputs do not match the requested vector amount");
        }
    }

    private static List<ExactStack> checkedStacks(
            List<ExactStack> source,
            String name) {
        List<ExactStack> copy = List.copyOf(Objects.requireNonNull(source, name));
        Set<AEKey> keys = new LinkedHashSet<>();
        // 一つの台帳で同じキーを複数行に分けず、再開時の部分会計を一意にする。
        for (ExactStack stack : copy) {
            ExactStack checked = Objects.requireNonNull(stack, name + " entry");
            if (!keys.add(checked.key())) {
                throw new IllegalArgumentException(
                        name + " contains a duplicate key");
            }
        }
        return copy;
    }

    private static List<String> checkedPatternIds(List<String> source) {
        List<String> copy = List.copyOf(
                Objects.requireNonNull(source, "requiredPatternIds"));
        Set<String> ids = new LinkedHashSet<>();
        // Pattern所有権は安定ID一件につき一度だけExecutorへ照会する。
        for (String rawId : copy) {
            String id = Objects.requireNonNull(rawId, "pattern id").trim();
            if (id.isEmpty() || !ids.add(id)) {
                throw new IllegalArgumentException(
                        "requiredPatternIds contains a blank or duplicate id");
            }
        }
        return List.copyOf(ids);
    }

    private static BigInteger requirePositive(BigInteger value, String name) {
        BigInteger checked = requireNonNegative(value, name);
        if (checked.signum() == 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return checked;
    }

    private static BigInteger requireNonNegative(
            BigInteger value,
            String name) {
        BigInteger checked = Objects.requireNonNull(value, name);
        if (checked.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return checked;
    }
}

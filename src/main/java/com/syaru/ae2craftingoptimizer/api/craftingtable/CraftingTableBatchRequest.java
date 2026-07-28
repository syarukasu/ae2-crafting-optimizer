package com.syaru.ae2craftingoptimizer.api.craftingtable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.syaru.ae2craftingoptimizer.api.vector.ExactStack;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 一つの確定作業台Patternを一つの物理Worker仕事へ渡す不変Request。
 *
 * <p>注文数量はThread数やassemble呼出し回数ではなく、実組立で証明した
 * 一回分の入出力へ掛ける係数としてだけ保持する。</p>
 */
public record CraftingTableBatchRequest(
        UUID transactionId,
        UUID ownerTransactionId,
        UUID craftingJobId,
        String payloadDigest,
        int stageIndex,
        CraftingTableBatchMode mode,
        IPatternDetails pattern,
        BigInteger executions,
        KeyCounter[] inputsPerExecution,
        List<ExactStack> aggregateSlotInputs,
        List<GenericStack> outputsPerExecution,
        List<GenericStack> remainingPerExecution,
        Map<AEKey, BigInteger> aggregateExpectedOutputs) {
    /** 破損または悪意あるRequestで巨大BigIntegerをAACへ渡さない固定上限。 */
    public static final int MAXIMUM_COUNT_BITS = 1_048_576;
    /** SHA-256表記と将来の接頭辞を収めるPayload文字列の防御上限。 */
    private static final int MAXIMUM_DIGEST_LENGTH = 128;

    public CraftingTableBatchRequest {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(ownerTransactionId, "ownerTransactionId");
        Objects.requireNonNull(craftingJobId, "craftingJobId");
        payloadDigest = Objects.requireNonNull(
                payloadDigest,
                "payloadDigest").trim();
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(pattern, "pattern");
        executions = requirePositive(executions, "executions");
        inputsPerExecution = copyCounters(inputsPerExecution);
        aggregateSlotInputs = checkedStacks(
                aggregateSlotInputs,
                "aggregateSlotInputs",
                false);
        outputsPerExecution = checkedGenericStacks(
                outputsPerExecution,
                "outputsPerExecution",
                true);
        remainingPerExecution = checkedGenericStacks(
                remainingPerExecution,
                "remainingPerExecution",
                false);
        aggregateExpectedOutputs = checkedCounts(
                aggregateExpectedOutputs,
                "aggregateExpectedOutputs");

        // 永続Receiptの識別情報が曖昧になる値は、Workerへ渡す前に拒否する。
        if (payloadDigest.isEmpty()
                || payloadDigest.length() > MAXIMUM_DIGEST_LENGTH
                || stageIndex < 0) {
            throw new IllegalArgumentException(
                    "invalid crafting-table batch identity");
        }

        Map<AEKey, BigInteger> expectedInputs =
                scaledInputTotals(
                        inputsPerExecution,
                        executions);
        Map<AEKey, BigInteger> suppliedInputs =
                mergeExactStacks(
                        aggregateSlotInputs);
        // ACO側のslot式と実際に所有権移転する合計が完全一致する場合だけ受理する。
        if (!expectedInputs.equals(suppliedInputs)) {
            throw new IllegalArgumentException(
                    "aggregate inputs do not match the crafting pattern coefficient");
        }

        Map<AEKey, BigInteger> expectedOutputs =
                scaledOutputTotals(
                        outputsPerExecution,
                        remainingPerExecution,
                        executions);
        // 主出力と返却物を含む式が親会計の期待値と完全一致することを確認する。
        if (!expectedOutputs.equals(
                aggregateExpectedOutputs)) {
            throw new IllegalArgumentException(
                    "aggregate outputs do not match the crafting pattern coefficient");
        }
    }

    @Override
    public KeyCounter[] inputsPerExecution() {
        return copyCounters(inputsPerExecution);
    }

    public Map<AEKey, BigInteger> aggregateInputTotals() {
        return mergeExactStacks(aggregateSlotInputs);
    }

    /**
     * NeoECOの通常Threadへ、各slotをsigned longのまま無損失で保存できるかを返す。
     *
     * <p>同じAEKeyが九slotに{@link Long#MAX_VALUE}ずつ存在する入力は、キー別合計だけが
     * longを越える。NeoECOは入力をslot由来の複数{@code GenericStack}として保持できるため、
     * 合計値で誤って拒否せず、個々の保存要素と出力だけを検査する。</p>
     */
    public boolean countsFitSignedLong() {
        BigInteger maximum =
                BigInteger.valueOf(Long.MAX_VALUE);
        // 入力はslot境界を保った各要素がlongへ収まれば、同一キー合計がlongを越えてもよい。
        for (ExactStack input :
                aggregateSlotInputs) {
            // 一slotでもlongへ収まらない場合は通常AE2 Threadへ保存できない。
            if (input.amount()
                            .compareTo(
                                    maximum)
                    > 0) {
                return false;
            }
        }
        return allFitSignedLong(
                aggregateExpectedOutputs);
    }

    private static boolean allFitSignedLong(
            Map<AEKey, BigInteger> counts) {
        BigInteger maximum =
                BigInteger.valueOf(Long.MAX_VALUE);
        // 標準AE2経路へ渡す全キーが正のsigned longへ無損失変換できるかを調べる。
        for (BigInteger amount : counts.values()) {
            // 一件でも上限を超える場合、標準KeyCounterへ落としてはならない。
            if (amount.compareTo(maximum) > 0) {
                return false;
            }
        }
        return true;
    }

    private static KeyCounter[] copyCounters(
            KeyCounter[] source) {
        Objects.requireNonNull(source, "inputsPerExecution");
        KeyCounter[] copy = new KeyCounter[source.length];
        // fillCraftingGridが入力を減算するため、Request境界ではslotごとに複製する。
        for (int slotIndex = 0;
                slotIndex < source.length;
                slotIndex++) {
            KeyCounter sourceCounter =
                    Objects.requireNonNull(
                            source[slotIndex],
                            "input slot");
            KeyCounter target =
                    copy[slotIndex] = new KeyCounter();
            // 一つのslotへ確定済みの候補と一回分数量をそのまま移す。
            for (var entry : sourceCounter) {
                // 非正数は有効な作業台入力を表さないため拒否する。
                if (entry.getLongValue() <= 0L) {
                    throw new IllegalArgumentException(
                            "input amounts must be positive");
                }
                target.add(
                        entry.getKey(),
                        entry.getLongValue());
            }
        }
        return copy;
    }

    private static List<ExactStack> checkedStacks(
            List<ExactStack> source,
            String name,
            boolean requireNonEmpty) {
        List<ExactStack> copy = List.copyOf(
                Objects.requireNonNull(source, name));
        // 必須一覧が空なら、数量式として成立しない。
        if (requireNonEmpty && copy.isEmpty()) {
            throw new IllegalArgumentException(
                    name + " must not be empty");
        }
        // ExactStack自身の検査に加え、Request全体のbit上限も適用する。
        for (ExactStack stack : copy) {
            Objects.requireNonNull(stack, name + " entry");
            requirePositive(stack.amount(), name + " amount");
        }
        return copy;
    }

    private static List<GenericStack> checkedGenericStacks(
            List<GenericStack> source,
            String name,
            boolean requireNonEmpty) {
        List<GenericStack> copy = List.copyOf(
                Objects.requireNonNull(source, name));
        // 主出力だけは一件以上必要で、返却物は空を許可する。
        if (requireNonEmpty && copy.isEmpty()) {
            throw new IllegalArgumentException(
                    name + " must not be empty");
        }
        // Pattern一回分にnullまたは非正数を残さない。
        for (GenericStack stack : copy) {
            // 不正要素が一件でもあれば係数を掛ける前に拒否する。
            if (stack == null || stack.amount() <= 0L) {
                throw new IllegalArgumentException(
                        name + " contains an invalid stack");
            }
        }
        return copy;
    }

    private static Map<AEKey, BigInteger> checkedCounts(
            Map<AEKey, BigInteger> source,
            String name) {
        Map<AEKey, BigInteger> copy =
                new LinkedHashMap<>();
        Objects.requireNonNull(source, name)
                .forEach((key, amount) -> {
                    Objects.requireNonNull(
                            key,
                            name + " key");
                    BigInteger checked =
                            requirePositive(
                                    amount,
                                    name + " amount");
                    copy.put(key, checked);
                });
        // 完成物のない作業台仕事は親会計を完了できない。
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(
                    name + " must not be empty");
        }
        return Map.copyOf(copy);
    }

    private static Map<AEKey, BigInteger> scaledInputTotals(
            KeyCounter[] perExecution,
            BigInteger executions) {
        Map<AEKey, BigInteger> result =
                new LinkedHashMap<>();
        // 同じAEKeyが複数slotにあっても一回分合計へ正確に畳み込む。
        for (KeyCounter slot : perExecution) {
            // slot内の確定候補をBigInteger係数へ変換する。
            for (var entry : slot) {
                result.merge(
                        entry.getKey(),
                        BigInteger.valueOf(
                                        entry.getLongValue())
                                .multiply(executions),
                        BigInteger::add);
            }
        }
        return Map.copyOf(result);
    }

    private static Map<AEKey, BigInteger> scaledOutputTotals(
            List<GenericStack> outputs,
            List<GenericStack> remaining,
            BigInteger executions) {
        Map<AEKey, BigInteger> result =
                new LinkedHashMap<>();
        mergeScaled(result, outputs, executions);
        mergeScaled(result, remaining, executions);
        return Map.copyOf(result);
    }

    private static void mergeScaled(
            Map<AEKey, BigInteger> target,
            List<GenericStack> source,
            BigInteger executions) {
        // 主出力と返却物の各キーへ同じ正確な実行係数を掛ける。
        for (GenericStack stack : source) {
            target.merge(
                    stack.what(),
                    BigInteger.valueOf(stack.amount())
                            .multiply(executions),
                    BigInteger::add);
        }
    }

    private static Map<AEKey, BigInteger> mergeExactStacks(
            List<ExactStack> source) {
        Map<AEKey, BigInteger> result =
                new LinkedHashMap<>();
        // slot境界を保持した一覧を、所有権検査用のキー別合計へ畳み込む。
        for (ExactStack stack : source) {
            result.merge(
                    stack.key(),
                    stack.amount(),
                    BigInteger::add);
        }
        return Map.copyOf(result);
    }

    private static BigInteger requirePositive(
            BigInteger value,
            String name) {
        BigInteger checked =
                Objects.requireNonNull(value, name);
        // 0以下または固定bit上限超過の数量を、物理設備へ渡さない。
        if (checked.signum() <= 0
                || checked.bitLength()
                        > MAXIMUM_COUNT_BITS) {
            throw new IllegalArgumentException(
                    name + " is outside the supported positive range");
        }
        return checked;
    }
}

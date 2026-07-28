package com.syaru.ae2craftingoptimizer.api.batch;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.syaru.ae2craftingoptimizer.api.vector.ExactCraftingInputSlot;
import com.syaru.ae2craftingoptimizer.api.vector.ExactStack;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.level.Level;

/**
 * 一意な作業台Patternを、入力slotと出力係数だけの不変式へ変換する。
 *
 * <p>代替候補を推測しない。Plannerが選んで永続化したslot入力を実Patternへ再照合する。</p>
 */
public final class ExactPatternFormula {
    private final IPatternDetails pattern;
    private final KeyCounter[] inputsPerExecution;
    private final List<GenericStack> outputsPerExecution;
    private final List<GenericStack> remainingPerExecution;

    private ExactPatternFormula(
            IPatternDetails pattern,
            KeyCounter[] inputsPerExecution,
            List<GenericStack> outputsPerExecution,
            List<GenericStack> remainingPerExecution) {
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        this.inputsPerExecution = copyCounters(inputsPerExecution);
        this.outputsPerExecution = List.copyOf(outputsPerExecution);
        this.remainingPerExecution = List.copyOf(remainingPerExecution);
    }

    public static Optional<ExactPatternFormula> tryCreate(
            IPatternDetails pattern,
            Level level) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(level, "level");
        List<ExactCraftingInputSlot> selected =
                new ArrayList<>();
        // 旧呼出は単一候補slotだけを自動選択し、複数候補を先頭へ縮退させない。
        for (IPatternDetails.IInput input :
                pattern.getInputs()) {
            GenericStack[] candidates =
                    input.getPossibleInputs();
            // Planner選択結果がない呼出では、一意な候補だけを式へ変換する。
            if (input.getMultiplier() <= 0L
                    || candidates.length != 1
                    || candidates[0] == null) {
                return Optional.empty();
            }
            try {
                selected.add(
                        new ExactCraftingInputSlot(
                                candidates[0].what(),
                                Math.multiplyExact(
                                        candidates[0].amount(),
                                        input.getMultiplier())));
            } catch (ArithmeticException
                    | IllegalArgumentException invalid) {
                return Optional.empty();
            }
        }
        return tryCreate(
                pattern,
                level,
                selected);
    }

    public static Optional<ExactPatternFormula> tryCreate(
            IPatternDetails pattern,
            Level level,
            List<ExactCraftingInputSlot> selectedInputs) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(level, "level");
        List<ExactCraftingInputSlot> selected =
                List.copyOf(
                        Objects.requireNonNull(
                                selectedInputs,
                                "selectedInputs"));
        /*
         * AACが自前で一度組み立てられる作業台Patternだけを受理する。
         * 加工Patternは入出力が固定でも外部機械へ実行を委譲すべきなので除外する。
         */
        if (!(pattern instanceof IMolecularAssemblerSupportedPattern)) {
            return Optional.empty();
        }
        try {
            IPatternDetails.IInput[] sourceInputs = pattern.getInputs();
            // Plannerが保存したslot数と現在Patternのslot数が違えば、世代変更として拒否する。
            if (sourceInputs.length
                    != selected.size()) {
                return Optional.empty();
            }
            KeyCounter[] inputs = new KeyCounter[sourceInputs.length];
            List<GenericStack> remaining = new ArrayList<>();
            for (int slotIndex = 0;
                    slotIndex < sourceInputs.length;
                    slotIndex++) {
                IPatternDetails.IInput input = sourceInputs[slotIndex];
                GenericStack[] candidates = input.getPossibleInputs();
                ExactCraftingInputSlot selectedInput =
                        selected.get(
                                slotIndex);
                // 0以下のslot係数はPattern式を作れないため、候補走査前に拒否する。
                if (input.getMultiplier() <= 0L) {
                    return Optional.empty();
                }
                boolean candidateMatched =
                        false;
                /*
                 * 保存済みの具体キーと係数が、現在Patternの明示候補に完全一致するか確認する。
                 * 候補順が変わっても同じキー・量なら受理し、別素材へは自動変更しない。
                 */
                for (GenericStack candidate :
                        candidates) {
                    // null、非正数、別キー候補は保存済み選択との比較対象にしない。
                    if (candidate == null
                            || candidate.amount() <= 0L
                            || !candidate.what()
                                    .equals(
                                            selectedInput.key())) {
                        continue;
                    }
                    long amount =
                            Math.multiplyExact(
                                    candidate.amount(),
                                    input.getMultiplier());
                    // キーと一回入力量の双方が一致した候補だけを選択済みと認める。
                    if (amount
                                    == selectedInput
                                            .amountPerExecution()
                            && input.isValid(
                                    selectedInput.key(),
                                    level)) {
                        candidateMatched =
                                true;
                        break;
                    }
                }
                // Pattern世代変更や無効な選択結果を推測で補わない。
                if (!candidateMatched) {
                    return Optional.empty();
                }
                KeyCounter counter = inputs[slotIndex] =
                        new KeyCounter();
                counter.add(
                        selectedInput.key(),
                        selectedInput.amountPerExecution());
                AEKey remainingKey =
                        input.getRemainingKey(
                                selectedInput.key());
                if (remainingKey != null) {
                    remaining.add(new GenericStack(
                            remainingKey,
                            input.getMultiplier()));
                }
            }
            List<GenericStack> outputs =
                    checkedStacks(pattern.getOutputs());
            if (outputs.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ExactPatternFormula(
                    pattern,
                    inputs,
                    outputs,
                    remaining));
        } catch (ArithmeticException | IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    public IPatternDetails pattern() {
        return pattern;
    }

    public KeyCounter[] copyInputsPerExecution() {
        return copyCounters(inputsPerExecution);
    }

    public List<GenericStack> outputsPerExecution() {
        return outputsPerExecution;
    }

    public List<GenericStack> remainingPerExecution() {
        return remainingPerExecution;
    }

    /**
     * slot境界を維持した入力一覧。同じキーが九slotにある場合も九行のまま返す。
     */
    public List<ExactStack> exactSlotInputs(BigInteger executions) {
        BigInteger checked = requirePositive(executions);
        List<ExactStack> result = new ArrayList<>();
        for (KeyCounter counter : inputsPerExecution) {
            for (var entry : counter) {
                result.add(new ExactStack(
                        entry.getKey(),
                        BigInteger.valueOf(entry.getLongValue())
                                .multiply(checked)));
            }
        }
        return List.copyOf(result);
    }

    /** 親CPU内部在庫から引くキー別合計。 */
    public Map<AEKey, BigInteger> exactInputTotals(
            BigInteger executions) {
        Map<AEKey, BigInteger> result = new LinkedHashMap<>();
        for (ExactStack stack : exactSlotInputs(executions)) {
            result.merge(
                    stack.key(),
                    stack.amount(),
                    BigInteger::add);
        }
        return Map.copyOf(result);
    }

    /** 実組立で検証した一回分出力へ係数を掛けたキー別合計。 */
    public Map<AEKey, BigInteger> exactExpectedOutputTotals(
            BigInteger executions) {
        BigInteger checked = requirePositive(executions);
        Map<AEKey, BigInteger> result = new LinkedHashMap<>();
        mergeScaled(result, outputsPerExecution, checked);
        mergeScaled(result, remainingPerExecution, checked);
        return Map.copyOf(result);
    }

    private static void mergeScaled(
            Map<AEKey, BigInteger> target,
            List<GenericStack> source,
            BigInteger executions) {
        for (GenericStack stack : source) {
            target.merge(
                    stack.what(),
                    BigInteger.valueOf(stack.amount())
                            .multiply(executions),
                    BigInteger::add);
        }
    }

    private static List<GenericStack> checkedStacks(
            GenericStack[] source) {
        List<GenericStack> result = new ArrayList<>(source.length);
        for (GenericStack stack : source) {
            if (stack == null || stack.amount() <= 0L) {
                throw new IllegalArgumentException(
                        "pattern output is empty");
            }
            result.add(stack);
        }
        return List.copyOf(result);
    }

    private static BigInteger requirePositive(BigInteger value) {
        BigInteger checked = Objects.requireNonNull(
                value,
                "executions");
        if (checked.signum() <= 0) {
            throw new IllegalArgumentException(
                    "executions must be positive");
        }
        return checked;
    }

    private static KeyCounter[] copyCounters(KeyCounter[] source) {
        KeyCounter[] copy = new KeyCounter[source.length];
        for (int index = 0; index < source.length; index++) {
            KeyCounter target = copy[index] = new KeyCounter();
            for (var entry : source[index]) {
                target.add(
                        entry.getKey(),
                        entry.getLongValue());
            }
        }
        return copy;
    }
}

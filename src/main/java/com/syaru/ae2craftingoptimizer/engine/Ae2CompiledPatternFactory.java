package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.syaru.ae2craftingoptimizer.util.StableFingerprint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

final class Ae2CompiledPatternFactory {
    private Ae2CompiledPatternFactory() {
    }

    @Nullable
    static CompiledPattern<AEKey> compile(IPatternDetails details, String id, Level level) {
        return compile(details, id, level, PlanningGuard.none());
    }

    @Nullable
    static CompiledPattern<AEKey> compile(
            IPatternDetails details,
            String id,
            Level level,
            PlanningGuard workBudget) {
        Objects.requireNonNull(workBudget, "workBudget");
        List<CompiledPattern.InputSlot<AEKey>> inputs = new ArrayList<>();
        int inspectedInputs = 0;
        // Pattern入力を一度ずつ変換し、長い加工PatternもAE2の計算時間枠へ参加させる。
        for (IPatternDetails.IInput input : details.getInputs()) {
            workBudget.checkpoint(++inspectedInputs);
            if (input.getMultiplier() <= 0L) {
                return null;
            }
            List<CompiledPattern.Stack<AEKey>> alternatives = new ArrayList<>();
            int inspectedAlternatives = 0;
            // 各slotの候補を検証し、曖昧性を隠したまま配列化しない。
            for (GenericStack possible : input.getPossibleInputs()) {
                workBudget.checkpoint(++inspectedAlternatives);
                if (possible.amount() <= 0L
                        || !input.isValid(possible.what(), level)
                        || input.getRemainingKey(possible.what()) != null) {
                    return null;
                }
                alternatives.add(new CompiledPattern.Stack<>(
                        possible.what(),
                        CheckedLongMath.multiply(possible.amount(), input.getMultiplier(), id + "/input")));
            }
            if (alternatives.isEmpty()) {
                return null;
            }
            inputs.add(new CompiledPattern.InputSlot<>(alternatives));
        }
        Map<AEKey, Long> outputs = new LinkedHashMap<>();
        int inspectedOutputs = 0;
        // 出力ごとの係数を検証し、同一キーだけを正確に集約する。
        for (GenericStack produced : details.getOutputs()) {
            workBudget.checkpoint(++inspectedOutputs);
            if (produced.amount() <= 0L) {
                return null;
            }
            CheckedLongMath.merge(outputs, produced.what(), produced.amount(), id + "/output");
        }
        return outputs.isEmpty()
                ? null
                : new CompiledPattern<>(id, inputs, outputs, details.supportsPushInputsToExternalInventory());
    }

    static String fingerprint(IPatternDetails details) {
        return fingerprint(details, PlanningGuard.none());
    }

    static String fingerprint(
            IPatternDetails details,
            PlanningGuard workBudget) {
        Objects.requireNonNull(workBudget, "workBudget");
        StringBuilder fingerprint = new StringBuilder(192);
        fingerprint.append(details.getClass().getName())
                .append('|')
                .append(details.getDefinition().toTagGeneric());
        int inspectedInputs = 0;
        // 入力定義を安定順でFingerprintへ加え、長いPatternでもtick予算を尊重する。
        for (IPatternDetails.IInput input : details.getInputs()) {
            workBudget.checkpoint(++inspectedInputs);
            fingerprint.append("|i:").append(input.getMultiplier());
            int inspectedAlternatives = 0;
            // slot内の全候補を定義順でFingerprintへ含める。
            for (GenericStack possible : input.getPossibleInputs()) {
                workBudget.checkpoint(++inspectedAlternatives);
                fingerprint.append(':')
                        .append(possible.what().toTagGeneric())
                        .append('@')
                        .append(possible.amount());
            }
        }
        int inspectedOutputs = 0;
        // 出力定義も同じ世代識別子へ含める。
        for (GenericStack output : details.getOutputs()) {
            workBudget.checkpoint(++inspectedOutputs);
            fingerprint.append("|o:")
                    .append(output.what().toTagGeneric())
                    .append('@')
                    .append(output.amount());
        }
        return StableFingerprint.sha256(fingerprint) + ':' + details.getDefinition().getId();
    }
}

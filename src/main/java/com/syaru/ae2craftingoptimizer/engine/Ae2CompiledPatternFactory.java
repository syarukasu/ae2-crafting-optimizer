package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.syaru.ae2craftingoptimizer.util.StableFingerprint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

final class Ae2CompiledPatternFactory {
    private Ae2CompiledPatternFactory() {
    }

    @Nullable
    static CompiledPattern<AEKey> compile(IPatternDetails details, String id, Level level) {
        List<CompiledPattern.InputSlot<AEKey>> inputs = new ArrayList<>();
        for (IPatternDetails.IInput input : details.getInputs()) {
            if (input.getMultiplier() <= 0L) {
                return null;
            }
            List<CompiledPattern.Stack<AEKey>> alternatives = new ArrayList<>();
            for (GenericStack possible : input.getPossibleInputs()) {
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
        for (GenericStack produced : details.getOutputs()) {
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
        StringBuilder fingerprint = new StringBuilder(192);
        fingerprint.append(details.getClass().getName())
                .append('|')
                .append(details.getDefinition().toTagGeneric());
        for (IPatternDetails.IInput input : details.getInputs()) {
            fingerprint.append("|i:").append(input.getMultiplier());
            for (GenericStack possible : input.getPossibleInputs()) {
                fingerprint.append(':')
                        .append(possible.what().toTagGeneric())
                        .append('@')
                        .append(possible.amount());
            }
        }
        for (GenericStack output : details.getOutputs()) {
            fingerprint.append("|o:")
                    .append(output.what().toTagGeneric())
                    .append('@')
                    .append(output.amount());
        }
        return StableFingerprint.sha256(fingerprint) + ':' + details.getDefinition().getId();
    }
}

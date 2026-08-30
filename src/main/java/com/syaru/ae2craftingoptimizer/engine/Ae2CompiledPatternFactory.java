package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
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

    /**
     * Pattern APIとLevelを読む部分だけを呼出threadで固定する。
     * SHA-256生成とグラフ解析は、この不変値を受け取ったplanning workerで行う。
     */
    @Nullable
    static Captured capture(IPatternDetails details, Level level) {
        // Level依存の代替候補を持つPatternは高速経路で証明できないため、API走査前にAE2へ返す。
        boolean exactInputDomain = hasExactInputDomain(details);
        if (!exactInputDomain) {
            return null;
        }
        AEItemKey definition = details.getDefinition();
        String definitionId = definition.getId().toString();
        List<CompiledPattern.InputSlot<AEKey>> inputs = new ArrayList<>();
        List<FingerprintInput> fingerprintInputs = new ArrayList<>();
        for (IPatternDetails.IInput input : details.getInputs()) {
            if (input.getMultiplier() <= 0L) {
                return null;
            }
            List<CompiledPattern.Stack<AEKey>> alternatives = new ArrayList<>();
            List<GenericStack> capturedAlternatives = new ArrayList<>();
            for (GenericStack possible : input.getPossibleInputs()) {
                if (possible.amount() <= 0L
                        || !input.isValid(possible.what(), level)
                        || input.getRemainingKey(possible.what()) != null) {
                    return null;
                }
                capturedAlternatives.add(new GenericStack(possible.what(), possible.amount()));
                alternatives.add(new CompiledPattern.Stack<>(
                        possible.what(),
                        CheckedLongMath.multiply(
                                possible.amount(),
                                input.getMultiplier(),
                                definitionId + "/input")));
            }
            if (alternatives.isEmpty()) {
                return null;
            }
            inputs.add(new CompiledPattern.InputSlot<>(alternatives));
            fingerprintInputs.add(new FingerprintInput(
                    input.getMultiplier(),
                    List.copyOf(capturedAlternatives)));
        }
        Map<AEKey, Long> outputs = new LinkedHashMap<>();
        List<GenericStack> fingerprintOutputs = new ArrayList<>();
        for (GenericStack produced : details.getOutputs()) {
            if (produced.amount() <= 0L) {
                return null;
            }
            fingerprintOutputs.add(new GenericStack(produced.what(), produced.amount()));
            CheckedLongMath.merge(
                    outputs,
                    produced.what(),
                    produced.amount(),
                    definitionId + "/output");
        }
        return outputs.isEmpty()
                ? null
                : new Captured(
                        details,
                        inputs,
                        outputs,
                        details.supportsPushInputsToExternalInventory(),
                        details.getClass().getName(),
                        definition,
                        fingerprintInputs,
                        fingerprintOutputs,
                        definitionId,
                         exactInputDomain);
    }

    /** Issue #167: Level依存の代替候補をworkerで再評価しない、検査済みAE2 Patternだけを許可する。 */
    private static boolean hasExactInputDomain(IPatternDetails details) {
        Class<?> implementation = details.getClass();
        if (implementation == AEProcessingPattern.class) {
            return true;
        }
        if (implementation == AECraftingPattern.class) {
            AECraftingPattern crafting = (AECraftingPattern) details;
            return !crafting.canSubstitute() && !crafting.canSubstituteFluids();
        }
        if (implementation == AEStonecuttingPattern.class) {
            return !((AEStonecuttingPattern) details).canSubstitute();
        }
        if (implementation == AESmithingTablePattern.class) {
            return !((AESmithingTablePattern) details).canSubstitute();
        }
        return false;
    }

    static final class Captured {
        private final IPatternDetails details;
        private final List<CompiledPattern.InputSlot<AEKey>> inputs;
        private final Map<AEKey, Long> outputs;
        private final boolean externalPush;
        private final String implementationName;
        private final AEItemKey definition;
        private final List<FingerprintInput> fingerprintInputs;
        private final List<GenericStack> fingerprintOutputs;
        private final String definitionId;
        private final boolean exactInputDomain;
        private volatile String fingerprint;

        private Captured(
                IPatternDetails details,
                List<CompiledPattern.InputSlot<AEKey>> inputs,
                Map<AEKey, Long> outputs,
                boolean externalPush,
                String implementationName,
                AEItemKey definition,
                List<FingerprintInput> fingerprintInputs,
                List<GenericStack> fingerprintOutputs,
                String definitionId,
                boolean exactInputDomain) {
            this.details = details;
            this.inputs = List.copyOf(inputs);
            this.outputs = Map.copyOf(outputs);
            this.externalPush = externalPush;
            this.implementationName = implementationName;
            this.definition = definition;
            this.fingerprintInputs = List.copyOf(fingerprintInputs);
            this.fingerprintOutputs = List.copyOf(fingerprintOutputs);
            this.definitionId = definitionId;
            this.exactInputDomain = exactInputDomain;
        }

        IPatternDetails details() {
            return details;
        }

        List<CompiledPattern.InputSlot<AEKey>> inputs() {
            return inputs;
        }

        boolean exactInputDomain() {
            return exactInputDomain;
        }

        String fingerprint() {
            String current = fingerprint;
            if (current != null) {
                return current;
            }
            synchronized (this) {
                current = fingerprint;
                if (current == null) {
                    current = createFingerprint();
                    fingerprint = current;
                }
                return current;
            }
        }

        CompiledPattern<AEKey> compile(String id) {
            return new CompiledPattern<>(id, inputs, outputs, externalPush);
        }

        private String createFingerprint() {
            StringBuilder material = new StringBuilder(192);
            material.append(implementationName)
                    .append('|')
                    .append(definition.toTagGeneric());
            // server threadで固定したslot順と候補順を、そのままfingerprintへ直列化する。
            for (FingerprintInput input : fingerprintInputs) {
                material.append("|i:").append(input.multiplier());
                for (GenericStack possible : input.alternatives()) {
                    material.append(':')
                            .append(possible.what().toTagGeneric())
                            .append('@')
                            .append(possible.amount());
                }
            }
            // AE2が返した出力順を保持し、旧fingerprint形式と同じ文字列を生成する。
            for (GenericStack output : fingerprintOutputs) {
                material.append("|o:")
                        .append(output.what().toTagGeneric())
                        .append('@')
                        .append(output.amount());
            }
            return StableFingerprint.sha256(material) + ':' + definitionId;
        }
    }

    private record FingerprintInput(long multiplier, List<GenericStack> alternatives) {
        private FingerprintInput {
            alternatives = List.copyOf(alternatives);
        }
    }
}

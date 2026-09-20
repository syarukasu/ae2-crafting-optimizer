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
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
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
        return capture(details, level, ignored -> { });
    }

    @Nullable
    static Captured capture(IPatternDetails details, Level level, Consumer<String> rejected) {
        // Issue #190: capture public structure; dynamic validity is observed per request on the server.
        boolean exactInputDomain = hasExactInputDomain(details);
        String implementation = details.getClass().getName();
        if (!exactInputDomain && (implementation.equals("com.extendedae_plus.api.crafting.ScaledProcessingPattern")
                || implementation.equals("com.extendedae_plus.api.crafting.ScaledProcessingPatternAdv"))) {
            // The known wrapper multiplies unchecked; an unverified original must never bypass its adapter.
            rejected.accept("unsupported_input_domain");
            return null;
        }
        AEItemKey definition = details.getDefinition();
        String definitionId = definition.getId().toString();
        HolderLookup.Provider registryAccess =
                com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require();
        List<CompiledPattern.InputSlot<AEKey>> inputs = new ArrayList<>();
        List<FingerprintInput> fingerprintInputs = new ArrayList<>();
        int slot = -1;
        for (IPatternDetails.IInput input : details.getInputs()) {
            slot++;
            if (input.getMultiplier() <= 0L) {
                rejected.accept("nonpositive_multiplier slot=" + slot);
                return null;
            }
            List<CompiledPattern.Stack<AEKey>> alternatives = new ArrayList<>();
            List<GenericStack> capturedAlternatives = new ArrayList<>();
            for (GenericStack possible : input.getPossibleInputs()) {
                if (possible.amount() <= 0L) {
                    rejected.accept("nonpositive_template slot=" + slot);
                    return null;
                }
                if (exactInputDomain && (!input.isValid(possible.what(), level)
                        || input.getRemainingKey(possible.what()) != null)) {
                    exactInputDomain = false;
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
                rejected.accept("empty_input slot=" + slot);
                return null;
            }
            inputs.add(new CompiledPattern.InputSlot<>(alternatives, capturedAlternatives.get(0).amount()));
            fingerprintInputs.add(new FingerprintInput(
                    input.getMultiplier(),
                    List.copyOf(capturedAlternatives)));
        }
        Map<AEKey, Long> outputs = new LinkedHashMap<>();
        List<GenericStack> fingerprintOutputs = new ArrayList<>();
        for (GenericStack produced : details.getOutputs()) {
            if (produced.amount() <= 0L) {
                rejected.accept("nonpositive_output key=" + produced.what().getId());
                return null;
            }
            fingerprintOutputs.add(new GenericStack(produced.what(), produced.amount()));
            CheckedLongMath.merge(
                    outputs,
                    produced.what(),
                    produced.amount(),
                    definitionId + "/output");
        }
        if (outputs.isEmpty()) {
            rejected.accept("empty_outputs");
            return null;
        }
        return new Captured(
                        details,
                        inputs,
                        outputs,
                        details.supportsPushInputsToExternalInventory(),
                        details.getClass().getName(),
                        definition,
                        fingerprintInputs,
                        fingerprintOutputs,
                        definitionId,
                        exactInputDomain,
                        registryAccess);
    }

    /** Issue #167: Level依存の代替候補をworkerで再評価しない、検査済みAE2 Patternだけを許可する。 */
    private static boolean hasExactInputDomain(IPatternDetails details) {
        Class<?> implementation = details.getClass();
        if (inheritsExactProcessingInputs(implementation)) {
            return true;
        }
        if (isExactScaledProcessingPattern(details)) {
            return true;
        }
        if (implementation == AECraftingPattern.class) {
            AECraftingPattern crafting = (AECraftingPattern) details;
            /*
             * AE2の流体代替は入力候補を増やさず、容器入力を単一の流体入力へ正規化する。
             * capture側は残余物と各候補の有効性を個別に検査するため、候補集合を変える
             * アイテム代替だけをexact domainの除外条件にする。
             */
            return !crafting.canSubstitute();
        }
        if (implementation == AEStonecuttingPattern.class) {
            return !((AEStonecuttingPattern) details).canSubstitute();
        }
        if (implementation == AESmithingTablePattern.class) {
            return !((AESmithingTablePattern) details).canSubstitute();
        }
        return false;
    }

    /** AdvancedAE's directional processing pattern inherits these exact AE2 inputs unchanged. */
    static boolean inheritsExactProcessingInputs(Class<?> implementation) {
        if (!AEProcessingPattern.class.isAssignableFrom(implementation)) return false;
        try {
            return implementation.getMethod("getInputs").getDeclaringClass() == AEProcessingPattern.class
                    && implementation.getMethod("getOutputs").getDeclaringClass() == AEProcessingPattern.class;
        } catch (NoSuchMethodException missingContract) {
            return false;
        }
    }

    /** Issue #190: checked adapter for EAEP's final, exact processing wrappers. */
    private static boolean isExactScaledProcessingPattern(IPatternDetails details) {
        String base = "com.extendedae_plus.api.crafting.ScaledProcessingPattern";
        Class<?> implementation = details.getClass();
        if (!implementation.getName().equals(base) && !implementation.getName().equals(base + "Adv")) {
            return false;
        }
        try {
            for (String method : List.of("getDefinition", "getInputs", "getOutputs")) {
                var declaration = implementation.getMethod(method);
                if (!declaration.getDeclaringClass().getName().equals(base)
                        || !java.lang.reflect.Modifier.isFinal(declaration.getModifiers())) return false;
            }
            var originalGetter = implementation.getMethod("getOriginal");
            var multiplierGetter = implementation.getMethod("getMultiplier");
            if (!originalGetter.getDeclaringClass().getName().equals(base)
                    || !multiplierGetter.getDeclaringClass().getName().equals(base)
                    || originalGetter.getReturnType() != AEProcessingPattern.class
                    || multiplierGetter.getReturnType() != long.class) return false;
            AEProcessingPattern original = (AEProcessingPattern) originalGetter.invoke(details);
            if (original == null || !inheritsExactProcessingInputs(original.getClass())) return false;
            validateScale(original, (Long) multiplierGetter.invoke(details));
            return true;
        } catch (java.lang.reflect.InvocationTargetException failed) {
            if (failed.getCause() instanceof Error fatal) throw fatal;
            throw new IllegalStateException("scaled processing pattern accessor failed", failed.getCause());
        } catch (ReflectiveOperationException unsupportedVersion) {
            return false;
        }
    }

    static void validateScale(IPatternDetails original, long scale) {
        if (scale <= 0) throw new IllegalArgumentException("scaled pattern multiplier must be positive");
        for (var input : original.getInputs()) {
            long multiplied = CheckedLongMath.multiply(input.getMultiplier(), scale, "scaled pattern/input multiplier");
            for (var possible : input.getPossibleInputs()) {
                CheckedLongMath.multiply(possible.amount(), multiplied, "scaled pattern/input amount");
            }
        }
        for (var output : original.getOutputs()) {
            CheckedLongMath.multiply(output.amount(), scale, "scaled pattern/output amount");
        }
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
        private final HolderLookup.Provider registryAccess;
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
                boolean exactInputDomain,
                HolderLookup.Provider registryAccess) {
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
            this.registryAccess = registryAccess;
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
                    .append(definition.toTagGeneric(registryAccess));
            // server threadで固定したslot順と候補順を、そのままfingerprintへ直列化する。
            for (FingerprintInput input : fingerprintInputs) {
                material.append("|i:").append(input.multiplier());
                for (GenericStack possible : input.alternatives()) {
                    material.append(':')
                            .append(possible.what().toTagGeneric(registryAccess))
                            .append('@')
                            .append(possible.amount());
                }
            }
            // AE2が返した出力順を保持し、旧fingerprint形式と同じ文字列を生成する。
            for (GenericStack output : fingerprintOutputs) {
                material.append("|o:")
                        .append(output.what().toTagGeneric(registryAccess))
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

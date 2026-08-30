package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.RecipeGenerationTracker;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.world.level.Level;

/** Calculation-local memoization for values that cannot change while one AE2 job is being solved. */
public final class CraftingCalculationMemo {
    private static final Set<String> IMMUTABLE_AE2_INPUT_TYPES = Set.of(
            "appeng.crafting.pattern.AECraftingPattern$Input",
            "appeng.crafting.pattern.AEProcessingPattern$Input",
            "appeng.crafting.pattern.AEStonecuttingPattern$Input",
            "appeng.crafting.pattern.AESmithingTablePattern$Input");
    private static final String PURE_PROCESSING_INPUT_TYPE =
            "appeng.crafting.pattern.AEProcessingPattern$Input";
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private CraftingCalculationMemo() {
    }

    public static void begin(Object calculation) {
        if (ACOConfig.memoizeCraftingCalculationQueries()) {
            CURRENT.set(new State(calculation));
        }
    }

    public static void end(Object calculation) {
        State state = CURRENT.get();
        if (state != null && state.calculation == calculation) {
            CURRENT.remove();
        }
    }

    public static boolean canEmit(Object calculation, ICraftingService service, AEKey key) {
        State state = state(calculation);
        if (state == null) {
            return service.canEmitFor(key);
        }
        return state.emittable.computeIfAbsent(key, service::canEmitFor);
    }

    public static Collection<IPatternDetails> patterns(
            Object calculation, ICraftingService service, AEKey key) {
        State state = state(calculation);
        if (state == null) {
            return service.getCraftingFor(key);
        }
        return state.patterns.computeIfAbsent(key, ignored -> List.copyOf(service.getCraftingFor(key)));
    }

    /** AE2標準候補の順序と内容を変えず、同一計算内の二度目以降だけ再利用する。 */
    public static Collection<IPatternDetails> patternCandidates(
            Object calculation,
            ICraftingService service,
            AEKey key) {
        return patterns(calculation, service, key);
    }

    /**
     * AE2本体所有Inputの読取専用候補配列だけを、一計算内へ固定する。
     * 外部Inputは動的metadataを持ち得るため、毎回その実装へ委譲する。
     */
    public static GenericStack[] possibleInputs(IPatternDetails.IInput input) {
        State state = currentState();
        if (state == null || !isImmutableAe2Input(input)) {
            return input.getPossibleInputs();
        }
        return state.possibleInputs.computeIfAbsent(
                input,
                ignored -> input.getPossibleInputs().clone());
    }

    public static AEKey fuzzyCraftable(
            Object calculation,
            ICraftingService service,
            IPatternDetails.IInput input,
            AEKey candidate,
            Supplier<AEKey> lookup) {
        State state = state(calculation);
        if (state == null) {
            return lookup.get();
        }
        // 外部Inputのfilterは可変状態を参照し得るため、結果をACO側へ固定しない。
        if (!isImmutableAe2Input(input)) {
            return lookup.get();
        }
        var byCandidate = state.fuzzy.computeIfAbsent(input, ignored -> new HashMap<>());
        return byCandidate.computeIfAbsent(candidate, ignored -> Optional.ofNullable(lookup.get())).orElse(null);
    }

    public static AEKey remainingKey(
            Object calculation, IPatternDetails.IInput input, AEKey template) {
        State state = state(calculation);
        if (state == null) {
            return input.getRemainingKey(template);
        }
        // 外部Inputの返却物規則はACOが不変性を証明できないため、その都度委譲する。
        if (!isImmutableAe2Input(input)) {
            return input.getRemainingKey(template);
        }
        var byTemplate = state.remaining.computeIfAbsent(input, ignored -> new HashMap<>());
        return byTemplate.computeIfAbsent(template, ignored -> Optional.ofNullable(input.getRemainingKey(template)))
                .orElse(null);
    }

    /** worldを読まないAEProcessingPatternの完全一致判定だけを一計算内で再利用する。 */
    public static boolean inputValid(
            IPatternDetails.IInput input,
            AEKey candidate,
            Level level,
            BooleanSupplier lookup) {
        State state = currentState();
        if (state == null || !isPureProcessingInput(input)) {
            return lookup.getAsBoolean();
        }
        var byLevel = state.validInputs.computeIfAbsent(input, ignored -> new IdentityHashMap<>());
        var byCandidate = byLevel.computeIfAbsent(level, ignored -> new HashMap<>());
        return byCandidate.computeIfAbsent(candidate, ignored -> lookup.getAsBoolean());
    }

    private static State state(Object calculation) {
        if (!ACOConfig.memoizeCraftingCalculationQueries()) {
            return null;
        }
        State state = CURRENT.get();
        if (state == null || state.calculation != calculation) {
            return null;
        }
        state.refreshAfterGenerationChange();
        return state;
    }

    private static State currentState() {
        if (!ACOConfig.memoizeCraftingCalculationQueries()) {
            return null;
        }
        State state = CURRENT.get();
        if (state == null) {
            return null;
        }
        state.refreshAfterGenerationChange();
        return state;
    }

    static boolean isMemoizableInputType(String implementationName) {
        return IMMUTABLE_AE2_INPUT_TYPES.contains(implementationName);
    }

    static boolean isPureValidationInputType(String implementationName) {
        return PURE_PROCESSING_INPUT_TYPE.equals(implementationName);
    }

    private static boolean isImmutableAe2Input(IPatternDetails.IInput input) {
        return input != null && isMemoizableInputType(input.getClass().getName());
    }

    private static boolean isPureProcessingInput(IPatternDetails.IInput input) {
        return input != null && isPureValidationInputType(input.getClass().getName());
    }

    private static final class State {
        private final Object calculation;
        private final Map<AEKey, Boolean> emittable = new HashMap<>();
        private final Map<AEKey, Collection<IPatternDetails>> patterns = new HashMap<>();
        private final IdentityHashMap<IPatternDetails.IInput, GenericStack[]> possibleInputs =
                new IdentityHashMap<>();
        private final Map<IPatternDetails.IInput, Map<AEKey, Optional<AEKey>>> fuzzy = new IdentityHashMap<>();
        private final Map<IPatternDetails.IInput, Map<AEKey, Optional<AEKey>>> remaining = new IdentityHashMap<>();
        private final Map<IPatternDetails.IInput, Map<Level, Map<AEKey, Boolean>>> validInputs =
                new IdentityHashMap<>();
        private long patternGeneration;
        private long recipeGeneration;

        private State(Object calculation) {
            this.calculation = calculation;
            this.patternGeneration = ProviderPatternGenerationTracker.generation();
            this.recipeGeneration = RecipeGenerationTracker.generation();
        }

        private void refreshAfterGenerationChange() {
            long currentPatternGeneration = ProviderPatternGenerationTracker.generation();
            long currentRecipeGeneration = RecipeGenerationTracker.generation();
            // 同じ二世代なら計算ローカルcacheをそのまま再利用する。
            if (patternGeneration == currentPatternGeneration
                    && recipeGeneration == currentRecipeGeneration) {
                return;
            }
            // 変更後も旧Pattern/remaining値を返さず、次のAE2照会から新世代をmemo化する。
            emittable.clear();
            patterns.clear();
            possibleInputs.clear();
            fuzzy.clear();
            remaining.clear();
            validInputs.clear();
            patternGeneration = currentPatternGeneration;
            recipeGeneration = currentRecipeGeneration;
        }
    }
}

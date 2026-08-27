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
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.world.level.Level;

/** Calculation-local memoization for values that cannot change while one AE2 job is being solved. */
public final class CraftingCalculationMemo {
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

    /** AE2が試す候補順を保ったDecision Programを、一計算および安全な世代間で再利用する。 */
    public static Collection<IPatternDetails> patternCandidates(
            Object calculation,
            ICraftingService service,
            AEKey key) {
        State state = state(calculation);
        if (state == null) {
            return PatternCandidatePruner.prune(service.getCraftingFor(key), key);
        }
        return state.candidates.computeIfAbsent(key, ignored -> {
            Ae2DecisionProgramCache.OutputDecisionProgram program =
                    Ae2DecisionProgramCache.getOrCompile(service, key);
            // compile中に世代が動いた場合は共有結果を使わず、この呼出をAE2現在値へ戻す。
            if (!state.isCurrent()) {
                return PatternCandidatePruner.prune(service.getCraftingFor(key), key);
            }
            program.copyInputsInto(state.decisionInputs);
            return program.patterns();
        });
    }

    /** AE2本体所有Inputの候補配列を、同じPattern世代中は再取得しない。 */
    public static GenericStack[] possibleInputs(IPatternDetails.IInput input) {
        State state = CURRENT.get();
        Ae2DecisionProgramCache.InputDecision decision = state == null || !state.isCurrent()
                ? null
                : state.decisionInputs.get(input);
        return decision == null ? input.getPossibleInputs() : decision.possibleInputs();
    }

    public static AEKey fuzzyCraftable(
            Object calculation,
            ICraftingService service,
            IPatternDetails.IInput input,
            AEKey candidate,
            Level level,
            Supplier<AEKey> lookup) {
        State state = state(calculation);
        if (state == null) {
            return lookup.get();
        }
        Ae2DecisionProgramCache.InputDecision decision = state.decisionInputs.get(input);
        // world非依存と証明したProcessing Inputだけを注文間で共有する。
        if (decision != null && decision.shareValidationResults()) {
            return decision.fuzzyCraftable(candidate, level, lookup);
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
        Ae2DecisionProgramCache.InputDecision decision = state.decisionInputs.get(input);
        // 任意CraftingRecipeの返却物は注文間へ固定せず、一計算内だけ再利用する。
        if (decision != null && decision.shareRemainingResults()) {
            return decision.remainingKey(template, () -> input.getRemainingKey(template));
        }
        var byTemplate = state.remaining.computeIfAbsent(input, ignored -> new HashMap<>());
        return byTemplate.computeIfAbsent(template, ignored -> Optional.ofNullable(input.getRemainingKey(template)))
                .orElse(null);
    }

    public static boolean inputValid(
            IPatternDetails.IInput input,
            AEKey candidate,
            Level level,
            BooleanSupplier lookup) {
        State state = CURRENT.get();
        // 計算外、設定OFF、recipe世代変更後はAE2の判定をそのまま実行する。
        if (!ACOConfig.memoizeCraftingCalculationQueries()
                || state == null
                || !state.isCurrent()) {
            return lookup.getAsBoolean();
        }
        // 外部IInputはworld依存の判定を持ち得るため、AE2本体所有の実装だけをメモ化する。
        if (!input.getClass().getName().startsWith("appeng.")) {
            return lookup.getAsBoolean();
        }
        Ae2DecisionProgramCache.InputDecision decision = state.decisionInputs.get(input);
        // Recipe#matches(Level)へ到達しない純粋な入力判定だけを注文間で共有する。
        if (decision != null && decision.shareValidationResults()) {
            return decision.inputValid(candidate, level, lookup);
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
        return state != null && state.calculation == calculation && state.isCurrent()
                ? state
                : null;
    }

    private static final class State {
        private final Object calculation;
        private final Map<AEKey, Boolean> emittable = new HashMap<>();
        private final Map<AEKey, Collection<IPatternDetails>> patterns = new HashMap<>();
        private final Map<AEKey, Collection<IPatternDetails>> candidates = new HashMap<>();
        private final IdentityHashMap<IPatternDetails.IInput, Ae2DecisionProgramCache.InputDecision>
                decisionInputs = new IdentityHashMap<>();
        private final Map<IPatternDetails.IInput, Map<AEKey, Optional<AEKey>>> fuzzy = new IdentityHashMap<>();
        private final Map<IPatternDetails.IInput, Map<AEKey, Optional<AEKey>>> remaining = new IdentityHashMap<>();
        private final Map<IPatternDetails.IInput, Map<Level, Map<AEKey, Boolean>>> validInputs =
                new IdentityHashMap<>();
        private final long patternGeneration = ProviderPatternGenerationTracker.generation();
        private final long recipeGeneration = RecipeGenerationTracker.generation();

        private State(Object calculation) {
            this.calculation = calculation;
        }

        private boolean isCurrent() {
            return patternGeneration == ProviderPatternGenerationTracker.generation()
                    && recipeGeneration == RecipeGenerationTracker.generation();
        }
    }
}

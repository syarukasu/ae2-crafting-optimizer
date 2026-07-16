package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

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
        var byCandidate = state.fuzzy.computeIfAbsent(input, ignored -> new HashMap<>());
        return byCandidate.computeIfAbsent(candidate, ignored -> Optional.ofNullable(lookup.get())).orElse(null);
    }

    public static AEKey remainingKey(
            Object calculation, IPatternDetails.IInput input, AEKey template) {
        State state = state(calculation);
        if (state == null) {
            return input.getRemainingKey(template);
        }
        var byTemplate = state.remaining.computeIfAbsent(input, ignored -> new HashMap<>());
        return byTemplate.computeIfAbsent(template, ignored -> Optional.ofNullable(input.getRemainingKey(template)))
                .orElse(null);
    }

    private static State state(Object calculation) {
        if (!ACOConfig.memoizeCraftingCalculationQueries()) {
            return null;
        }
        State state = CURRENT.get();
        return state != null && state.calculation == calculation ? state : null;
    }

    private static final class State {
        private final Object calculation;
        private final Map<AEKey, Boolean> emittable = new HashMap<>();
        private final Map<AEKey, Collection<IPatternDetails>> patterns = new HashMap<>();
        private final Map<IPatternDetails.IInput, Map<AEKey, Optional<AEKey>>> fuzzy = new IdentityHashMap<>();
        private final Map<IPatternDetails.IInput, Map<AEKey, Optional<AEKey>>> remaining = new IdentityHashMap<>();

        private State(Object calculation) {
            this.calculation = calculation;
        }
    }
}

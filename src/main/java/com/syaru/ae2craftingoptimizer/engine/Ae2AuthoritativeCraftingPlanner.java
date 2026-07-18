package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.AEConfig;
import appeng.crafting.CraftingPlan;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * AE2標準計算と同じ結果を証明できる線形Patternだけを置き換えるPlanner。
 * 証明条件を一つでも満たせない場合はnullを返し、呼出側がAE2標準経路を実行する。
 */
public final class Ae2AuthoritativeCraftingPlanner {
    private static final Set<String> LOGGED_FALLBACKS = ConcurrentHashMap.newKeySet();

    private Ae2AuthoritativeCraftingPlanner() {
    }

    @Nullable
    public static Capture capture(
            Level level,
            IGrid grid,
            IActionSource source,
            KeyCounter networkSnapshot) {
        if (!ACOConfig.enableAuthoritativeCompiledPlanner()
                || level == null
                || grid == null
                || networkSnapshot == null) {
            return null;
        }
        return new Capture(
                level,
                grid,
                source,
                counterMap(networkSnapshot),
                ProviderPatternGenerationTracker.generation(),
                RecipeGenerationTracker.generation());
    }

    @Nullable
    public static ICraftingPlan tryPlan(
            @Nullable Capture capture,
            AEKey output,
            long requestedAmount,
            CalculationStrategy strategy) {
        if (!ACOConfig.enableAuthoritativeCompiledPlanner()
                || capture == null
                || output == null
                || strategy == null
                || requestedAmount <= 0L
                || Thread.currentThread().isInterrupted()) {
            return null;
        }

        try {
            capture.requireCurrentGenerations();
            Ae2CompiledCraftingGraphCache.Snapshot snapshot =
                    Ae2CompiledCraftingGraphCache.getOrCompile(capture.grid(), capture.level());
            StrictTopology topology = StrictTopology.inspect(capture, snapshot, output);
            if (topology == null) {
                return null;
            }

            Map<AEKey, Long> planningInventory = new LinkedHashMap<>(capture.inventory());
            // AE2は完成品そのものを在庫から取り出さず、必ず要求されたクラフトとして計算する。
            planningInventory.remove(output);
            PlanningGuard guard = expanded -> {
                if ((expanded & 63) == 0) {
                    capture.requireCurrentGenerations();
                    if (Thread.currentThread().isInterrupted()) {
                        throw new PlanningCancelledException(expanded);
                    }
                }
            };
            var planned = new SymbolicCraftingPlanner<AEKey>().tryPlanLong(
                    snapshot.graph(),
                    output,
                    requestedAmount,
                    planningInventory,
                    topology.emittable(),
                    guard);
            if (planned.isEmpty()) {
                return null;
            }
            LongCraftingPlan<AEKey> symbolic = planned.get();
            if (!symbolic.craftable() && strategy == CalculationStrategy.CRAFT_LESS) {
                // CRAFT_LESSの部分成功探索はAE2へ任せる。ここで近似すると結果数量が変わる。
                return null;
            }

            Map<IPatternDetails, Long> patternTimes = new LinkedHashMap<>();
            for (var entry : symbolic.patternExecutions().entrySet()) {
                IPatternDetails details = snapshot.pattern(entry.getKey());
                if (details == null || entry.getValue() <= 0L) {
                    return null;
                }
                patternTimes.merge(details, entry.getValue(), Math::addExact);
            }
            if (patternTimes.size() > ACOConfig.getCraftingEngineShadowMaximumPatterns()) {
                return null;
            }

            long bytes = topology.calculateExactBytes(
                    output, requestedAmount, symbolic.patternExecutions());
            CraftingPlan result = new CraftingPlan(
                    new GenericStack(output, requestedAmount),
                    bytes,
                    !symbolic.craftable(),
                    false,
                    keyCounter(symbolic.usedInventory()),
                    keyCounter(symbolic.emitted()),
                    keyCounter(symbolic.missing()),
                    Map.copyOf(patternTimes));

            // 非同期計算中に在庫かPatternが変わった結果を返さない。
            capture.requireCurrentGenerations();
            if (!capture.inventory().equals(snapshotLiveInventory(capture))) {
                return null;
            }
            return result;
        } catch (PlanningCancelledException
                | StalePlanningSnapshotException
                | ArithmeticException ignored) {
            return null;
        } catch (Throwable failure) {
            logFallbackOnce(output, failure);
            return null;
        }
    }

    private static void logFallbackOnce(AEKey output, Throwable failure) {
        String key = output.getId() + ":" + failure.getClass().getName();
        if (LOGGED_FALLBACKS.add(key)) {
            AE2CraftingOptimizer.LOGGER.debug(
                    "ACO authoritative planner fell back to AE2 for {}: {}",
                    output.getId(),
                    failure.toString());
        }
    }

    private static Map<AEKey, Long> snapshotLiveInventory(Capture capture) {
        KeyCounter current = new KeyCounter();
        if (capture.source() == null) {
            return Map.of();
        }
        var storage = capture.grid().getStorageService();
        for (var entry : storage.getCachedInventory()) {
            long amount = AEConfig.instance().isCraftingSimulatedExtraction()
                    ? storage.getInventory().extract(
                            entry.getKey(), entry.getLongValue(), Actionable.SIMULATE, capture.source())
                    : entry.getLongValue();
            if (amount > 0L) {
                current.add(entry.getKey(), amount);
            }
        }
        return counterMap(current);
    }

    private static Map<AEKey, Long> counterMap(KeyCounter counter) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (var entry : counter) {
            if (entry.getLongValue() > 0L) {
                CheckedLongMath.merge(result, entry.getKey(), entry.getLongValue(), "authoritative/inventory");
            }
        }
        return Map.copyOf(result);
    }

    private static KeyCounter keyCounter(Map<AEKey, Long> counts) {
        KeyCounter result = new KeyCounter();
        counts.forEach((key, amount) -> {
            if (amount <= 0L) {
                throw new IllegalArgumentException("crafting plan counters must be positive");
            }
            result.add(key, amount);
        });
        return result;
    }

    public record Capture(
            Level level,
            IGrid grid,
            IActionSource source,
            Map<AEKey, Long> inventory,
            long patternGeneration,
            long recipeGeneration) {
        public Capture {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(grid, "grid");
            inventory = Map.copyOf(Objects.requireNonNull(inventory, "inventory"));
            if (patternGeneration < 0L || recipeGeneration < 0L) {
                throw new IllegalArgumentException("generation values must not be negative");
            }
        }

        private void requireCurrentGenerations() {
            long currentPattern = ProviderPatternGenerationTracker.generation();
            long currentRecipe = RecipeGenerationTracker.generation();
            if (currentPattern != patternGeneration || currentRecipe != recipeGeneration) {
                throw new StalePlanningSnapshotException(
                        new PlanningGenerationSnapshot(patternGeneration, 0L, recipeGeneration), 0);
            }
        }
    }

    private static final class StrictTopology {
        private final Ae2CompiledCraftingGraphCache.Snapshot snapshot;
        private final Map<AEKey, CompiledPattern<AEKey>> patternByOutput;
        private final Set<AEKey> emittable;

        private StrictTopology(
                Ae2CompiledCraftingGraphCache.Snapshot snapshot,
                Map<AEKey, CompiledPattern<AEKey>> patternByOutput,
                Set<AEKey> emittable) {
            this.snapshot = snapshot;
            this.patternByOutput = Map.copyOf(patternByOutput);
            this.emittable = Set.copyOf(emittable);
        }

        @Nullable
        static StrictTopology inspect(
                Capture capture,
                Ae2CompiledCraftingGraphCache.Snapshot snapshot,
                AEKey root) {
            ICraftingService service = capture.grid().getCraftingService();
            Map<AEKey, CompiledPattern<AEKey>> selected = new LinkedHashMap<>();
            Set<AEKey> emitters = new LinkedHashSet<>();
            Set<AEKey> visitedCrafted = new LinkedHashSet<>();
            Set<AEKey> stack = new LinkedHashSet<>();
            if (!inspectNode(
                    capture,
                    snapshot,
                    service,
                    root,
                    selected,
                    emitters,
                    visitedCrafted,
                    stack)) {
                return null;
            }
            return new StrictTopology(snapshot, selected, emitters);
        }

        private static boolean inspectNode(
                Capture capture,
                Ae2CompiledCraftingGraphCache.Snapshot snapshot,
                ICraftingService service,
                AEKey key,
                Map<AEKey, CompiledPattern<AEKey>> selected,
                Set<AEKey> emitters,
                Set<AEKey> visitedCrafted,
                Set<AEKey> stack) {
            if (Thread.currentThread().isInterrupted() || snapshot.graph().isCyclic(key)) {
                return false;
            }
            if (service.canEmitFor(key)) {
                emitters.add(key);
                return true;
            }
            if (snapshot.graph().patternsFor(key).isEmpty()) {
                return !snapshot.isIncompletelyCompiled(key)
                        && snapshot.registeredPatternCount(key) == 0;
            }
            if (!snapshot.hasExactlyOneFullyCompiledPattern(key)
                    || !visitedCrafted.add(key)
                    || !stack.add(key)) {
                return false;
            }
            CompiledPattern<AEKey> pattern = snapshot.graph().patternsFor(key).get(0);
            if (pattern.outputs().size() != 1 || pattern.outputAmount(key) <= 0L) {
                return false;
            }
            IPatternDetails details = snapshot.pattern(pattern.id());
            if (details == null || details.getInputs().length != pattern.inputs().size()) {
                return false;
            }
            selected.put(key, pattern);
            for (int slot = 0; slot < pattern.inputs().size(); slot++) {
                var compiledInput = pattern.inputs().get(slot);
                var realInput = details.getInputs()[slot];
                if (compiledInput.alternatives().size() != 1
                        || realInput.getPossibleInputs().length != 1) {
                    return false;
                }
                AEKey inputKey = compiledInput.alternatives().get(0).key();
                if (!realInput.isValid(inputKey, capture.level())
                        || hasFuzzyInventoryAlternative(capture.inventory().keySet(), inputKey, realInput, capture.level())) {
                    return false;
                }
                if (service.getCraftingFor(inputKey).isEmpty()) {
                    AEKey fuzzy = service.getFuzzyCraftable(
                            inputKey, candidate -> realInput.isValid(candidate, capture.level()));
                    if (fuzzy != null && !fuzzy.equals(inputKey)) {
                        return false;
                    }
                }
                if (!inspectNode(
                        capture,
                        snapshot,
                        service,
                        inputKey,
                        selected,
                        emitters,
                        visitedCrafted,
                        stack)) {
                    return false;
                }
            }
            stack.remove(key);
            return true;
        }

        private static boolean hasFuzzyInventoryAlternative(
                Set<AEKey> inventoryKeys,
                AEKey expected,
                IPatternDetails.IInput input,
                Level level) {
            AEKey expectedPrimary = expected.dropSecondary();
            for (AEKey candidate : inventoryKeys) {
                if (!candidate.equals(expected)
                        && candidate.dropSecondary().equals(expectedPrimary)
                        && input.isValid(candidate, level)) {
                    return true;
                }
            }
            return false;
        }

        Set<AEKey> emittable() {
            return emittable;
        }

        long calculateExactBytes(
                AEKey root,
                long requestedAmount,
                Map<String, Long> executions) {
            return ExactCraftingByteCounter.calculate(
                    root,
                    requestedAmount,
                    patternByOutput,
                    executions,
                    key -> key.getType().getAmountPerByte());
        }
    }
}

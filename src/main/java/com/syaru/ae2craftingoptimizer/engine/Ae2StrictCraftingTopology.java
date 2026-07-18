package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * AE2標準結果と一致すると証明できる単一路線だけを表す。
 * タグ代替、複数Pattern、再帰、同じ中間素材の再訪を含む場合は生成せずAE2へ戻す。
 */
final class Ae2StrictCraftingTopology {
    private final Map<AEKey, CompiledPattern<AEKey>> patternByOutput;
    private final Set<AEKey> emittable;

    private Ae2StrictCraftingTopology(
            Map<AEKey, CompiledPattern<AEKey>> patternByOutput,
            Set<AEKey> emittable) {
        this.patternByOutput = Map.copyOf(patternByOutput);
        this.emittable = Set.copyOf(emittable);
    }

    @Nullable
    static Ae2StrictCraftingTopology inspect(
            Level level,
            IGrid grid,
            Set<AEKey> inventoryKeys,
            Ae2CompiledCraftingGraphCache.Snapshot snapshot,
            AEKey root) {
        ICraftingService service = grid.getCraftingService();
        Map<AEKey, CompiledPattern<AEKey>> selected = new LinkedHashMap<>();
        Set<AEKey> emitters = new LinkedHashSet<>();
        Set<AEKey> visitedCrafted = new LinkedHashSet<>();
        Set<AEKey> stack = new LinkedHashSet<>();
        if (!inspectNode(
                level,
                snapshot,
                service,
                root,
                inventoryKeys,
                selected,
                emitters,
                visitedCrafted,
                stack)) {
            return null;
        }
        return new Ae2StrictCraftingTopology(selected, emitters);
    }

    private static boolean inspectNode(
            Level level,
            Ae2CompiledCraftingGraphCache.Snapshot snapshot,
            ICraftingService service,
            AEKey key,
            Set<AEKey> inventoryKeys,
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
            if (!realInput.isValid(inputKey, level)
                    || hasFuzzyInventoryAlternative(inventoryKeys, inputKey, realInput, level)) {
                return false;
            }
            if (service.getCraftingFor(inputKey).isEmpty()) {
                AEKey fuzzy = service.getFuzzyCraftable(
                        inputKey, candidate -> realInput.isValid(candidate, level));
                if (fuzzy != null && !fuzzy.equals(inputKey)) {
                    return false;
                }
            }
            if (!inspectNode(
                    level,
                    snapshot,
                    service,
                    inputKey,
                    inventoryKeys,
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

    Map<AEKey, CompiledPattern<AEKey>> patternByOutput() {
        return patternByOutput;
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

    BigInteger calculateBigExactBytes(
            AEKey root,
            BigInteger requestedAmount,
            Map<String, BigInteger> executions,
            int maximumBits) {
        return BigExactCraftingByteCounter.calculate(
                root,
                requestedAmount,
                patternByOutput,
                executions,
                key -> key.getType().getAmountPerByte(),
                maximumBits);
    }
}

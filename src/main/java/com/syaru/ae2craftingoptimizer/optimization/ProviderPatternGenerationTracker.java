package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Tracks exact provider content generations so identical refresh notifications do not rebuild AE2 indexes. */
public final class ProviderPatternGenerationTracker {
    private static final Map<IGridNode, Snapshot> SNAPSHOTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicLong GENERATION = new AtomicLong(1L);

    private ProviderPatternGenerationTracker() {
    }

    public static boolean shouldRefresh(IGridNode node) {
        if (!ACOConfig.trackProviderPatternGenerations()) {
            return true;
        }
        Snapshot current = snapshot(node);
        synchronized (SNAPSHOTS) {
            Snapshot previous = SNAPSHOTS.put(node, current);
            if (current.equals(previous)) {
                return false;
            }
        }
        GENERATION.updateAndGet(value -> value == Long.MAX_VALUE ? 1L : value + 1L);
        return true;
    }

    public static void remember(IGridNode node) {
        if (!ACOConfig.trackProviderPatternGenerations()) {
            return;
        }
        synchronized (SNAPSHOTS) {
            SNAPSHOTS.put(node, snapshot(node));
        }
    }

    public static void forget(IGridNode node) {
        synchronized (SNAPSHOTS) {
            SNAPSHOTS.remove(node);
        }
        GENERATION.updateAndGet(value -> value == Long.MAX_VALUE ? 1L : value + 1L);
    }

    public static long generation() {
        return GENERATION.get();
    }

    public static void clear() {
        synchronized (SNAPSHOTS) {
            SNAPSHOTS.clear();
        }
        GENERATION.updateAndGet(value -> value == Long.MAX_VALUE ? 1L : value + 1L);
    }

    private static Snapshot snapshot(IGridNode node) {
        ICraftingProvider provider = node.getService(ICraftingProvider.class);
        if (provider == null) {
            return Snapshot.EMPTY;
        }
        List<PatternSnapshot> patterns = new ArrayList<>();
        for (IPatternDetails pattern : provider.getAvailablePatterns()) {
            patterns.add(PatternSnapshot.of(pattern));
        }
        Set<AEKey> emitables = new HashSet<>(provider.getEmitableItems());
        return new Snapshot(provider.getPatternPriority(), List.copyOf(patterns), Set.copyOf(emitables));
    }

    private record Snapshot(int priority, List<PatternSnapshot> patterns, Set<AEKey> emitables) {
        private static final Snapshot EMPTY = new Snapshot(0, List.of(), Set.of());
    }

    private record PatternSnapshot(
            String implementation,
            AEKey definition,
            List<GenericStack> outputs,
            List<InputSnapshot> inputs,
            boolean externalPush) {
        private static PatternSnapshot of(IPatternDetails pattern) {
            List<InputSnapshot> inputs = new ArrayList<>();
            for (IPatternDetails.IInput input : pattern.getInputs()) {
                inputs.add(new InputSnapshot(
                        input.getMultiplier(),
                        List.of(input.getPossibleInputs().clone())));
            }
            return new PatternSnapshot(
                    pattern.getClass().getName(),
                    pattern.getDefinition(),
                    List.of(pattern.getOutputs().clone()),
                    List.copyOf(inputs),
                    pattern.supportsPushInputsToExternalInventory());
        }
    }

    private record InputSnapshot(long multiplier, List<GenericStack> possibleInputs) {
    }
}

package com.ae2vm.addon.nativeengine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.crafting.pattern.AEProcessingPattern;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.MinecraftServer;

/** #218: immutable metadata only. Never stores stock, plans or live input observations. */
// Historical detached-catalog fixture; excluded from every runtime artifact.
final class NativeVmPatternCatalog {
    private static final int MAX_GRIDS = 16;
    private static final int MAX_ENTRIES = 65_536;
    private static final Map<IGrid, Entry> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<AEKey, List<IPatternDetails>> producers;
    private final Set<AEKey> emitters;
    private final Map<IPatternDetails, NativePattern> nativePatterns;

    private NativeVmPatternCatalog(Builder builder) {
        producers = Map.copyOf(builder.producers);
        emitters = Set.copyOf(builder.emitters);
        nativePatterns = Collections.unmodifiableMap(new IdentityHashMap<>(builder.nativePatterns));
    }

    static synchronized NativeVmPatternCatalog cached(IGrid grid, MinecraftServer server, long epoch, long recipes) {
        var entry = CACHE.get(grid);
        if (entry == null) return null;
        if (entry.server() == server && entry.epoch() == epoch && entry.recipes() == recipes) return entry.catalog();
        CACHE.remove(grid);
        return null;
    }

    static synchronized void invalidate(IGrid grid) { CACHE.remove(grid); }
    static synchronized void recipesChanged() { CACHE.clear(); }
    static synchronized void stop(MinecraftServer server) {
        CACHE.values().removeIf(entry -> entry.server() == server);
    }

    private static synchronized void publish(IGrid grid, MinecraftServer server, long epoch, long recipes,
            NativeVmPatternCatalog catalog) {
        CACHE.put(grid, new Entry(server, epoch, recipes, catalog));
        while (CACHE.size() > MAX_GRIDS) CACHE.remove(CACHE.keySet().iterator().next());
    }

    static NativeVmPatternCatalog build(IGrid grid, MinecraftServer server, long epoch, long recipes,
            NativeVmCapture capture) {
        var keys = capture.callServer(NativeVmTiming.ServerCall.CATALOG, () -> {
            var available = grid.getCraftingService().getCraftables(key -> true);
            return available.size() > MAX_ENTRIES ? null : List.copyOf(available);
        });
        if (keys == null) return null;
        var builder = new Builder(keys);
        while (!capture.callServer(NativeVmTiming.ServerCall.CATALOG, () -> builder.advance(grid, capture))) { }
        if (builder.oversized) return null;
        return capture.callServer(NativeVmTiming.ServerCall.CATALOG, () -> {
            var result = new NativeVmPatternCatalog(builder);
            publish(grid, server, epoch, recipes, result);
            return result;
        });
    }

    List<IPatternDetails> producers(AEKey key) { return producers.getOrDefault(key, List.of()); }
    boolean emittable(AEKey key) { return emitters.contains(key); }
    NativePattern nativePattern(IPatternDetails pattern) { return nativePatterns.get(pattern); }

    record NativePattern(NativeVmCapture.Shape shape, AEItemKey definition) { }
    private record Entry(MinecraftServer server, long epoch, long recipes, NativeVmPatternCatalog catalog) { }

    private static final class Builder {
        private final List<AEKey> keys;
        private final Map<AEKey, List<IPatternDetails>> producers = new LinkedHashMap<>();
        private final Set<AEKey> emitters = new LinkedHashSet<>();
        private final Map<IPatternDetails, NativePattern> nativePatterns = new IdentityHashMap<>();
        private int keyIndex, patternIndex, entries;
        private List<IPatternDetails> current;
        private boolean oversized;

        Builder(List<AEKey> keys) { this.keys = keys; }

        boolean advance(IGrid grid, NativeVmCapture capture) {
            long deadline = capture.now() + 2_000_000L;
            int work = 0;
            while (keyIndex < keys.size() && work < 32 && capture.now() < deadline) {
                capture.checkCancellation();
                if (current == null) {
                    var key = keys.get(keyIndex);
                    var service = grid.getCraftingService();
                    var choices = service.getCraftingFor(key);
                    if (choices.size() > MAX_ENTRIES - entries) { oversized = true; return true; }
                    entries += choices.size();
                    current = List.copyOf(choices);
                    producers.put(key, current);
                    if (service.canEmitFor(key)) emitters.add(key);
                    patternIndex = 0;
                    work++;
                } else if (patternIndex < current.size()) {
                    var pattern = current.get(patternIndex++);
                    // Unknown callbacks must not be invoked for an unused recipe.
                    if (pattern.getClass() == AEProcessingPattern.class && !nativePatterns.containsKey(pattern)) {
                        try {
                            var inputs = pattern.getInputs();
                            boolean exact = java.util.Arrays.stream(inputs).allMatch(input ->
                                    input.getClass().getName().equals("appeng.crafting.pattern.AEProcessingPattern$Input")
                                            && input.getPossibleInputs().length == 1);
                            if (exact) nativePatterns.put(pattern, new NativePattern(
                                    NativeVmCapture.shape(pattern), pattern.getDefinition()));
                        } catch (RuntimeException invalid) {
                            // Only demand may surface a malformed pattern, never an unrelated order.
                        }
                    }
                    work++;
                } else {
                    current = null;
                    keyIndex++;
                }
            }
            return keyIndex == keys.size();
        }
    }
}

package com.ae2vm.addon.nativeengine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.AEItemKey;
import com.ae2vm.addon.compiler.PatternCompiler;
import com.ae2vm.addon.vm.CraftingVM;
import com.ae2vm.addon.vm.VmSimulationState;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import net.minecraft.world.level.Level;

/** Historical detached-capture fixture; excluded from every runtime artifact. */
final class NativeVmCapture {
    private final IGrid grid;
    private final IGrid patternGrid;
    private final ICraftingSimulationRequester requester;
    private final Level level;
    private final Thread worker = Thread.currentThread();
    private volatile boolean cancelled;
    private final long epoch;
    private final long recipeEpoch;
    private final VmAccounting.Stock stock;
    private final NativeVmPatternCatalog catalog;
    private final Set<String> sharedNativePatterns = new LinkedHashSet<>();
    private final Map<AEKey, List<IPatternDetails>> programs = new LinkedHashMap<>();
    private final Map<AEKey, List<IPatternDetails>> producers = new LinkedHashMap<>();
    private final Map<AEKey, Boolean> emitters = new LinkedHashMap<>();
    private final Set<AEKey> demandedKeys = new LinkedHashSet<>();
    private final Set<String> demandedPatterns = new LinkedHashSet<>();
    private final Map<AEKey, RuntimeException> deferredFailures = new LinkedHashMap<>();
    private final Map<IPatternDetails, String> liveIds = new IdentityHashMap<>();
    private final Map<IPatternDetails, String> frozenIds = new IdentityHashMap<>();
    private final Map<String, FrozenPattern> frozen = new LinkedHashMap<>();
    private final Map<String, Captured> patterns = new LinkedHashMap<>();
    private final Map<Slot, FrozenInput> inputs = new LinkedHashMap<>();
    private final Map<Query, Observation> observations = new LinkedHashMap<>();
    private final Set<Query> consultedQueries = new LinkedHashSet<>();
    private final java.util.function.LongConsumer progress;
    private final NativeVmTiming timing;

    NativeVmCapture(IGrid grid, Level level, VmAccounting accounting) {
        this(grid, level, accounting, ignored -> { });
    }
    NativeVmCapture(IGrid grid, Level level, VmAccounting accounting, java.util.function.LongConsumer progress) {
        this(grid, level, accounting, progress, new NativeVmTiming());
    }
    NativeVmCapture(IGrid grid, Level level, VmAccounting accounting, java.util.function.LongConsumer progress,
            NativeVmTiming timing) {
        this(grid, level, accounting, progress, timing, null);
    }

    NativeVmCapture(IGrid grid, Level level, VmAccounting accounting, java.util.function.LongConsumer progress,
            NativeVmTiming timing, ICraftingSimulationRequester requester) {
        this.grid = grid;
        this.requester = requester;
        this.level = level;
        this.progress = progress;
        this.timing = timing;
        Start start;
        try (var span = timing.phase(NativeVmTiming.Phase.STOCK)) {
        start = callServer(NativeVmTiming.ServerCall.STOCK, () -> {
            var owner = currentPatternGrid();
            var source = requester == null ? null : requester.getActionSource();
            VmAccounting.Stock counts;
            if (requester != null && source == null) counts = new VmAccounting.Stock(Map.of(), true, java.util.Set.of());
            else if (accounting == null) {
                var ordinary = new VmAccounting() {
                    public BigInteger maximumCount() { return BigInteger.valueOf(Long.MAX_VALUE); }
                    public Stock exactStock(IGrid value) { return ordinaryStock(value.getStorageService().getInventory().getAvailableStacks()); }
                    public appeng.api.networking.crafting.ICraftingPlan wideResult(IGrid value, Level world, NativeVmResult result) {
                        throw new IllegalStateException("wide result requires an accounting owner");
                    }
                };
                counts = requester == null ? ordinary.exactStock(grid) : ordinary.requestStock(grid, source, simulatedExtraction());
            } else counts = requester == null ? accounting.exactStock(grid)
                    : accounting.requestStock(grid, source, simulatedExtraction());
            long version = owner == null ? 0 : LegacyVmState.epoch(owner);
            long recipes = LegacyVmState.recipeEpoch();
            boolean shared = owner != null && owner.getCraftingService() instanceof NativeVmHook;
            var ready = shared ? NativeVmPatternCatalog.cached(owner, level.getServer(), version, recipes) : null;
            return new Start(owner, version, recipes, counts, shared, ready);
        }, false);
        }
        patternGrid = start.patternGrid();
        epoch = start.epoch();
        recipeEpoch = start.recipes();
        stock = start.stock();
        try (var span = timing.phase(NativeVmTiming.Phase.PREPARE)) {
        catalog = start.shared() && start.catalog() == null
                ? NativeVmPatternCatalog.build(patternGrid, level.getServer(), epoch, recipeEpoch, this)
                : start.catalog();
        }
    }

    private static boolean simulatedExtraction() {
        var config = appeng.core.AEConfig.instance();
        return config != null && config.isCraftingSimulatedExtraction();
    }

    private IGrid currentPatternGrid() {
        if (requester == null) return grid;
        var node = requester.getGridNode();
        return node == null ? null : node.getGrid();
    }

    private static VmAccounting.Stock ordinaryStock(KeyCounter values) {
        Map<AEKey, BigInteger> result = new LinkedHashMap<>();
        for (var entry : values) {
            if (entry.getLongValue() < 0) throw new IllegalArgumentException("negative native stock");
            if (entry.getLongValue() > 0) result.put(entry.getKey(), BigInteger.valueOf(entry.getLongValue()));
        }
        return new VmAccounting.Stock(result, true, result.keySet());
    }

    /** #218: the VM requests recipes only after consuming available intermediate stock. */
    VmQuantities plan(AEKey root, BigInteger requested, boolean craftLess) {
        try (var scope = PatternCompiler.openScope()) {
            var vm = new CraftingVM(this, key -> {
                var choices = candidates(key);
                return choices.isEmpty() ? null : choices.get(0);
            });
            vm.setAllPatternsResolver(this::candidates);
            vm.setEmitterResolver(this::emittable);
            vm.setProgressListener(progress);
            com.ae2vm.addon.vm.VmCraftingPlan result;
            try (var span = timing.phase(NativeVmTiming.Phase.VM)) {
                try {
                    result = vm.execute(PatternCompiler.compileRequest(root, requested),
                            new VmSimulationState(stock.amounts()), craftLess);
                } finally { timing.execution(vm.stateCopies(), vm.branchTrials(), vm.branchEntries(), vm.aggregatedBlocks()); }
            }
            Map<String, BigInteger> crafts = new LinkedHashMap<>();
            result.patternTimes().forEach((pattern, count) -> crafts.merge(
                    Objects.requireNonNull(frozenIds.get(pattern)), count, BigInteger::add));
            return new VmQuantities(root, result.finalOutput().amount(), crafts,
                    result.usedItems().quantities(), result.emittedItems().quantities(),
                    result.missingItems().quantities(), result.bytes(), Map.of(),
                    result.multiplePaths(), vm.instructions(), vm.work(), vm.skippedIterations());
        }
    }

    private final class FrozenInput implements PatternCompiler.DetachedInput {
        private final Slot slot;
        private final GenericStack[] templates;
        private final long multiplier;
        private final AEKey craftedKey;
        private final boolean emittable;
        private final boolean nativeExact;
        FrozenInput(Slot slot, GenericStack[] templates, long multiplier, AEKey craftedKey, boolean emittable,
                boolean nativeExact) {
            this.slot = slot; this.templates = templates; this.multiplier = multiplier;
            this.craftedKey = craftedKey; this.emittable = emittable;
            this.nativeExact = nativeExact;
        }
        // Private captured arrays are consumed read-only by this request's compiler and VM.
        public GenericStack[] getPossibleInputs() { return templates; }
        public long getMultiplier() { return multiplier; }
        public AEKey craftedKey() { return craftedKey; }
        public boolean emittable() { return emittable; }
        public AEKey exactKey() {
            if (!nativeExact) return null;
            var primary = observe(slot, templates[0].what());
            return primary.valid() && primary.remainder() == null ? templates[0].what() : null;
        }
        public void prepareCandidates(java.util.Collection<AEKey> keys) {
            if (nativeExact) return;
            var pending = new java.util.LinkedHashSet<Query>();
            for (var key : keys) {
                var query = new Query(slot, key);
                if (!observations.containsKey(query)) pending.add(query);
            }
            prepareQueries(new ArrayList<>(pending));
        }
        public boolean isCandidatePrepared(AEKey key) { return nativeExact || observations.containsKey(new Query(slot, key)); }
        public boolean isValid(AEKey key, Level ignored) {
            if (nativeExact) return key.matches(templates[0]) && observe(slot, key).valid();
            return observe(slot, key).valid();
        }
        public AEKey getRemainingKey(AEKey key) {
            return nativeExact && !key.matches(templates[0]) ? null : observe(slot, key).remainder();
        }
    }
    private record FrozenPattern(AEItemKey definition, IPatternDetails.IInput[] inputs, GenericStack[] outputs)
            implements PatternCompiler.DetachedPattern {
        public AEItemKey getDefinition() { return definition; }
        public IPatternDetails.IInput[] getInputs() { return inputs; }
        public GenericStack[] getOutputs() { return outputs; }
    }

    boolean emittable(AEKey key) {
        if (catalog != null && grid == patternGrid) return catalog.emittable(key);
        return emitters.computeIfAbsent(key, k -> callServer(NativeVmTiming.ServerCall.EMITTERS,
                () -> grid.getCraftingService().canEmitFor(k)));
    }

    private List<IPatternDetails> candidates(AEKey key) {
        if (patternGrid == null) return List.of();
        if (deferredFailures.containsKey(key)) throw deferredFailures.get(key);
        var cached = programs.get(key);
        if (cached != null) return demand(key, cached);
        bounded(programs.size());
        if (catalog != null) {
            var choices = catalog.producers(key);
            if (choices.stream().allMatch(pattern -> catalog.nativePattern(pattern) != null)) {
                var values = new ArrayList<IPatternDetails>();
                for (var pattern : choices) values.add(freezeNative(pattern, catalog.nativePattern(pattern)));
                var value = List.copyOf(values);
                producers.put(key, choices);
                programs.put(key, value);
                return demand(key, value);
            }
        }
        var result = new ArrayList<IPatternDetails>();
        var primaries = new java.util.ArrayDeque<Query>();
        boolean complete = false;
        while (!complete) {
            complete = callServer(NativeVmTiming.ServerCall.PRODUCERS, () -> {
                long deadline = timing.now() + 2_000_000L;
                var current = producers.computeIfAbsent(key,
                        ignored -> List.copyOf(patternGrid.getCraftingService().getCraftingFor(key)));
                int captured = 0, observed = 0;
                do {
                    if (!primaries.isEmpty()) {
                        captureQuery(primaries.remove());
                        observed++;
                    } else if (result.size() < current.size() && captured < 32) {
                        result.add(freeze(current.get(result.size()), primaries));
                        captured++;
                    } else break;
                } while (observed < 128 && timing.now() < deadline);
                boolean finished = result.size() == current.size() && primaries.isEmpty();
                if (finished && current.size() == 1 && stock.complete())
                    prefetchNativeInputs(result.get(0), deadline, 32 - captured, 128 - observed);
                return finished;
            });
            progress.accept(observations.size());
        }
        var value = List.copyOf(result);
        programs.put(key, value);
        return demand(key, value);
    }

    private List<IPatternDetails> demand(AEKey key, List<IPatternDetails> choices) {
        demandedKeys.add(key);
        for (var pattern : choices) demandedPatterns.add(Objects.requireNonNull(frozenIds.get(pattern)));
        return choices;
    }

    /** #218: bounded metadata lookahead, not execution or a shared stock-dependent plan cache. */
    private void prefetchNativeInputs(IPatternDetails first, long deadline, int patternBudget, int observationBudget) {
        var pending = new java.util.ArrayDeque<AEKey>();
        var visited = new java.util.HashSet<AEKey>();
        enqueueNativeInputs(first, pending);
        int examined = 0;
        while (!pending.isEmpty() && examined++ < 32 && patternBudget > 0 && observationBudget > 0
                && timing.now() < deadline) {
            checkCancellation();
            var key = pending.remove();
            if (!visited.add(key) || programs.containsKey(key) || deferredFailures.containsKey(key)) continue;
            try {
                bounded(programs.size());
                var choices = producers.computeIfAbsent(key,
                        ignored -> List.copyOf(patternGrid.getCraftingService().getCraftingFor(key)));
                if (choices.isEmpty()) { programs.put(key, List.of()); continue; }
                if (choices.size() != 1 || choices.get(0).getClass() != appeng.crafting.pattern.AEProcessingPattern.class)
                    continue;
                // Never leave a partially observed speculative pattern in the ready catalog.
                if (choices.get(0).getInputs().length * 2 > observationBudget) break;
                var primaries = new java.util.ArrayDeque<Query>();
                var pattern = freeze(choices.get(0), primaries);
                patternBudget--;
                while (!primaries.isEmpty()) { captureQuery(primaries.remove()); observationBudget--; }
                programs.put(key, List.of(pattern));
                enqueueNativeInputs(pattern, pending);
            } catch (LegacyVmState.Changed | CancellationException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                // An unused branch is not a dependency. Surface the original error only if demanded.
                deferredFailures.put(key, failure);
            }
        }
    }

    private void enqueueNativeInputs(IPatternDetails frozenPattern, java.util.ArrayDeque<AEKey> pending) {
        var captured = patterns.get(frozenIds.get(frozenPattern));
        if (captured.pattern().getClass() != appeng.crafting.pattern.AEProcessingPattern.class) return;
        for (var value : frozenPattern.getInputs()) {
            var input = (FrozenInput) value;
            var key = input.exactKey();
            if (key != null && !input.emittable && stock.amount(key).signum() == 0) pending.add(key);
        }
    }

    /** Called only inside a bounded server slice; never expands a descendant recipe. */
    private FrozenPattern freeze(IPatternDetails pattern, java.util.ArrayDeque<Query> primaries) {
        checkCancellation();
        if (catalog != null && catalog.nativePattern(pattern) != null)
            return freezeNative(pattern, catalog.nativePattern(pattern));
        String id = liveIds.get(pattern);
        if (id != null) return frozen.get(id);
        bounded(patterns.size());
        id = "vm-" + patterns.size();
        var captured = new Captured(pattern, shape(pattern), pattern.getDefinition());
        var capturedInputs = new LinkedHashMap<Slot, FrozenInput>();
        var capturedQueries = new java.util.ArrayDeque<Query>();
        var sources = pattern.getInputs();
        var slots = new IPatternDetails.IInput[captured.shape().inputs().size()];
        for (int slot = 0; slot < slots.length; slot++) {
            checkCancellation();
            var identity = new Slot(id, slot);
            var shape = captured.shape().inputs().get(slot);
            var templates = shape.templates().toArray(GenericStack[]::new);
            var source = sources[slot];
            var crafted = selectCrafted(source, shape.templates());
            var input = new FrozenInput(identity, templates, shape.multiplier(),
                    crafted, patternGrid.getCraftingService().canEmitFor(templates[0].what()),
                    nativeExactInput(pattern, source, templates.length));
            capturedInputs.put(identity, input);
            slots[slot] = input;
            capturedQueries.add(new Query(identity, templates[0].what()));
            if (!crafted.equals(templates[0].what())) capturedQueries.add(new Query(identity, crafted));
        }
        var value = new FrozenPattern(captured.definition(), slots,
                captured.shape().outputs().toArray(GenericStack[]::new));
        // Failed speculation must not leave an alias pointing at a half-captured pattern.
        checkCancellation();
        patterns.put(id, captured);
        liveIds.put(pattern, id);
        inputs.putAll(capturedInputs);
        primaries.addAll(capturedQueries);
        frozen.put(id, value);
        frozenIds.put(value, id);
        timing.patternType(pattern.getClass().getName());
        return value;
    }

    private FrozenPattern freezeNative(IPatternDetails pattern, NativeVmPatternCatalog.NativePattern metadata) {
        checkCancellation();
        var existing = liveIds.get(pattern);
        if (existing != null) return frozen.get(existing);
        bounded(patterns.size());
        String id = "vm-" + patterns.size();
        var slots = new IPatternDetails.IInput[metadata.shape().inputs().size()];
        var pendingInputs = new LinkedHashMap<Slot, FrozenInput>();
        var pendingObservations = new LinkedHashMap<Query, Observation>();
        for (int index = 0; index < slots.length; index++) {
            checkCancellation();
            var shape = metadata.shape().inputs().get(index);
            var templates = shape.templates().toArray(GenericStack[]::new);
            var key = templates[0].what();
            stock.amount(key);
            var slot = new Slot(id, index);
            var input = new FrozenInput(slot, templates, shape.multiplier(), key, catalog.emittable(key), true);
            slots[index] = input;
            pendingInputs.put(slot, input);
            pendingObservations.put(new Query(slot, key), new Observation(true, null));
        }
        var value = new FrozenPattern(metadata.definition(), slots, metadata.shape().outputs().toArray(GenericStack[]::new));
        patterns.put(id, new Captured(pattern, metadata.shape(), metadata.definition()));
        liveIds.put(pattern, id);
        inputs.putAll(pendingInputs);
        observations.putAll(pendingObservations);
        frozen.put(id, value);
        frozenIds.put(value, id);
        sharedNativePatterns.add(id);
        timing.patternType(pattern.getClass().getName());
        return value;
    }

    private AEKey selectCrafted(IPatternDetails.IInput source, List<GenericStack> templates) {
        checkCancellation();
        var service = patternGrid.getCraftingService();
        AEKey encoded = templates.get(0).what();
        boolean emitted = service.canEmitFor(encoded);
        checkCancellation();
        if (!emitted && service.getCraftingFor(encoded).isEmpty()) {
            for (var template : templates) {
                checkCancellation();
                if (template.amount() != templates.get(0).amount()) continue;
                AEKey selected = service.getFuzzyCraftable(template.what(), key -> source.isValid(key, level));
                if (selected != null) return selected;
            }
        }
        checkCancellation();
        return encoded;
    }

    private Observation observe(Slot slot, AEKey key) {
        var query = new Query(slot, key);
        if (!observations.containsKey(query)) prepareQueries(List.of(query));
        var observation = Objects.requireNonNull(observations.get(query));
        consultedQueries.add(query);
        if (observation.failure() != null) throw observation.failure();
        return observation;
    }

    private void prepareQueries(List<Query> queries) {
        for (int start = 0; start < queries.size();) {
            final int first = start;
            start = callServer(NativeVmTiming.ServerCall.INPUTS, () -> {
                long deadline = timing.now() + 2_000_000L;
                int i = first;
                do {
                    captureQuery(queries.get(i++), true);
                } while (i < queries.size() && i - first < 128 && timing.now() < deadline);
                return i;
            });
            progress.accept(observations.size());
        }
    }

    private void captureQuery(Query query) {
        captureQuery(query, false);
    }

    private void captureQuery(Query query, boolean speculative) {
        checkCancellation();
        if (observations.containsKey(query)) return;
        var slot = query.slot();
        if (observations.size() >= 1_048_576)
            throw new IllegalStateException("VM observed input budget exceeded: patterns=" + patterns.size()
                    + ", observations=" + observations.size() + ", pattern=" + slot.id()
                    + ", slot=" + slot.index() + ", key=" + query.key());
        stock.amount(query.key());
        var input = patterns.get(slot.id()).pattern().getInputs()[slot.index()];
        Observation observation;
        try {
            observation = read(input, query.key());
        } catch (LegacyVmState.Changed | CancellationException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            if (!speculative) throw failure;
            // Unused lookahead is not a plan dependency. Preserve the original failure if later consulted.
            observation = new Observation(false, null, failure);
        }
        observations.put(query, observation);
    }

    long now() { return timing.now(); }

    void checkCancellation() {
        if (worker.isInterrupted()) cancelled = true;
        if (cancelled) throw new CancellationException("VM capture cancelled");
    }

    private Observation read(IPatternDetails.IInput input, AEKey key) {
        boolean valid = input.isValid(key, level);
        boolean primary = input.getPossibleInputs()[0].what().equals(key);
        return new Observation(valid, valid || primary ? input.getRemainingKey(key) : null);
    }

    void validate() {
        try (var span = timing.phase(NativeVmTiming.Phase.VALIDATE)) {
        // Stock is intentionally NOT compared: the result describes its snapshot;
        // AE2/exact execution must reserve against current storage at submission.
        List<Runnable> checks = new ArrayList<>();
        // #218: unchanged registered native patterns are immutable; dynamic callbacks are not.
        if (catalog != null) callServer(NativeVmTiming.ServerCall.VALIDATE, () -> null);
        for (var entry : producers.entrySet()) if (demandedKeys.contains(entry.getKey())
                && (catalog == null || entry.getValue().stream().anyMatch(pattern -> catalog.nativePattern(pattern) == null))) checks.add(() -> {
            if (!entry.getValue().equals(List.copyOf(patternGrid.getCraftingService().getCraftingFor(entry.getKey()))))
                throw new LegacyVmState.Changed("provider binding changed");
        });
        for (var entry : patterns.entrySet()) if (demandedPatterns.contains(entry.getKey())
                && !sharedNativePatterns.contains(entry.getKey())) checks.add(() -> {
            var captured = entry.getValue();
            if (!captured.shape().equals(shape(captured.pattern()))) throw new LegacyVmState.Changed("pattern shape changed");
        });
        for (var entry : inputs.entrySet()) if (demandedPatterns.contains(entry.getKey().id())
                && !sharedNativePatterns.contains(entry.getKey().id())) checks.add(() -> {
            var actual = patterns.get(entry.getKey().id()).pattern().getInputs()[entry.getKey().index()];
            var expected = entry.getValue();
            if ((expected.nativeExact && !nativeExactInput(patterns.get(entry.getKey().id()).pattern(),
                    actual, actual.getPossibleInputs().length))
                    || !Objects.equals(expected.craftedKey(), selectCrafted(actual, List.of(expected.templates)))
                    || expected.emittable() != patternGrid.getCraftingService().canEmitFor(expected.templates[0].what()))
                throw new LegacyVmState.Changed("input selection changed");
        });
        for (var entry : observations.entrySet()) if (consultedQueries.contains(entry.getKey())
                && demandedPatterns.contains(entry.getKey().slot().id())
                && !sharedNativePatterns.contains(entry.getKey().slot().id())) checks.add(() -> {
            var slot = entry.getKey().slot();
            var actual = patterns.get(slot.id()).pattern().getInputs()[slot.index()];
            if (!entry.getValue().equals(read(actual, entry.getKey().key()))) throw new LegacyVmState.Changed("input observation changed");
        });
        for (var entry : emitters.entrySet()) checks.add(() -> {
            if (entry.getValue() != grid.getCraftingService().canEmitFor(entry.getKey())) throw new LegacyVmState.Changed("emitter changed");
        });
        for (int start = 0; start < checks.size();) {
            final int first = start;
            start = callServer(NativeVmTiming.ServerCall.VALIDATE, () -> {
                long deadline = timing.now() + 2_000_000L;
                int index = first;
                do { checks.get(index++).run(); }
                while (index < checks.size() && index - first < 32 && timing.now() < deadline);
                return index;
            });
        }
        }
    }

    Map<String, IPatternDetails> bindings() {
        Map<String, IPatternDetails> result = new LinkedHashMap<>();
        patterns.forEach((id, value) -> { if (demandedPatterns.contains(id)) result.put(id, value.pattern()); });
        return Map.copyOf(result);
    }

    boolean widePendingOutputs(Map<String, BigInteger> executions) {
        Map<AEKey, BigInteger> total = new LinkedHashMap<>();
        for (var entry : executions.entrySet()) {
            for (var output : patterns.get(entry.getKey()).shape().outputs()) {
                total.merge(output.what(), BigInteger.valueOf(output.amount()).multiply(entry.getValue()), BigInteger::add);
            }
        }
        return total.values().stream().anyMatch(value -> value.bitLength() > 63);
    }

    <T> T callServer(Supplier<T> action) { return callServer(NativeVmTiming.ServerCall.OTHER, action); }
    <T> T callServer(NativeVmTiming.ServerCall kind, Supplier<T> action) { return callServer(kind, action, true); }
    private <T> T callServer(NativeVmTiming.ServerCall kind, Supplier<T> action, boolean validateEpoch) {
        var server = Objects.requireNonNull(level.getServer());
        Supplier<T> checked = () -> {
            if (cancelled || worker.isInterrupted() || server.isStopped()) throw new CancellationException("VM cancelled");
            if (validateEpoch && (currentPatternGrid() != patternGrid
                    || (patternGrid != null && epoch != LegacyVmState.epoch(patternGrid)) || recipeEpoch != LegacyVmState.recipeEpoch()))
                throw new LegacyVmState.Changed("pattern/recipe epoch changed");
            return action.get();
        };
        if (server.isSameThread()) return checked.get();
        long submitted = timing.now();
        try {
        var future = server.submit(() -> {
            long started = timing.now();
            try { return checked.get(); }
            finally { timing.serverFinished(kind, submitted, started, timing.now()); }
        });
        try { return future.get(); }
        catch (InterruptedException interrupted) {
            cancelled = true;
            future.cancel(false); Thread.currentThread().interrupt(); throw new CancellationException("VM interrupted");
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failed.getCause() instanceof Error fatal) throw fatal;
            throw new IllegalStateException(failed.getCause());
        }
        } finally { timing.unblocked(submitted); }
    }

    static Shape shape(IPatternDetails pattern) {
        List<SlotShape> inputs = new ArrayList<>();
        for (var input : pattern.getInputs()) {
            var templates = List.of(input.getPossibleInputs().clone());
            if (input.getMultiplier() <= 0 || templates.isEmpty() || templates.stream().anyMatch(s -> s.amount() <= 0))
                throw new IllegalArgumentException("invalid native pattern input");
            inputs.add(new SlotShape(input.getMultiplier(), templates));
        }
        var outputs = List.of(pattern.getOutputs().clone());
        if (outputs.isEmpty() || outputs.stream().anyMatch(s -> s.amount() <= 0))
            throw new IllegalArgumentException("invalid native pattern output");
        return new Shape(List.copyOf(inputs), outputs);
    }

    private static boolean nativeExactInput(IPatternDetails pattern, IPatternDetails.IInput input, int templates) {
        if (templates != 1) return false;
        if (input.getClass().getName().equals("appeng.crafting.pattern.AEProcessingPattern$Input")) return true;
        // #218: only the pinned native implementation rejects every non-primary key without a recipe callback.
        return pattern.getClass() == appeng.crafting.pattern.AECraftingPattern.class
                && !((appeng.crafting.pattern.AECraftingPattern) pattern).canSubstitute()
                && input.getClass().getName().equals("appeng.crafting.pattern.AECraftingPattern$Input");
    }

    private static void bounded(int count) {
        if (count >= 1_048_576) throw new IllegalStateException("VM capture resident limit exceeded");
    }
    private record Start(IGrid patternGrid, long epoch, long recipes, VmAccounting.Stock stock,
            boolean shared, NativeVmPatternCatalog catalog) { }
    record SlotShape(long multiplier, List<GenericStack> templates) { }
    record Shape(List<SlotShape> inputs, List<GenericStack> outputs) { }
    private record Captured(IPatternDetails pattern, Shape shape, AEItemKey definition) { }
    private record Slot(String id, int index) { }
    private record Query(Slot slot, AEKey key) { }
    private record Observation(boolean valid, AEKey remainder, RuntimeException failure) {
        Observation(boolean valid, AEKey remainder) { this(valid, remainder, null); }
    }
}

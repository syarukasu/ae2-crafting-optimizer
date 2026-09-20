package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.Consumer;
import net.minecraft.world.level.Level;

/** Issue #190: bounded, request-local API observations; all live callbacks run on the owning server. */
final class Ae2BranchingInputRules implements BranchingInputRules<AEKey> {
    interface ServerCall { <T> T call(Supplier<T> action); }
    private static final int MAX_OBSERVATIONS = 1_048_576;
    private final Ae2PlanningGraphSnapshot snapshot;
    private final Ae2PlanningInventorySnapshot inventory;
    private final Supplier<ICraftingService> service;
    private final Level level;
    private final ServerCall server;
    private final PlanningGuard guard;
    private final Thread owner = Thread.currentThread();
    private final Map<Slot, Input<AEKey>> inputs = new LinkedHashMap<>();
    private final Map<Slot, IPatternDetails.IInput> bindings = new LinkedHashMap<>();
    private final Map<Query, Observation<AEKey>> observations = new LinkedHashMap<>();
    private final Map<String, String> fingerprints = new LinkedHashMap<>();
    private final Map<String, CompiledPattern<AEKey>> fixedPatterns = new LinkedHashMap<>();

    Ae2BranchingInputRules(Ae2PlanningGraphSnapshot snapshot, Ae2PlanningInventorySnapshot inventory,
            Supplier<ICraftingService> service, Level level, ServerCall server, PlanningGuard guard) {
        this.snapshot = snapshot;
        this.inventory = inventory;
        this.service = service;
        this.level = level;
        this.server = server;
        this.guard = guard;
    }

    @Override
    public Input<AEKey> input(CompiledPattern<AEKey> pattern, int slot) {
        Slot id = new Slot(pattern.id(), slot);
        Input<AEKey> cached = inputs.get(id);
        if (cached != null) return cached;
        guard.checkpoint(inputs.size());
        if (inputs.size() >= MAX_OBSERVATIONS) throw new IllegalStateException("input snapshot limit exceeded");
        var compiledSlot = pattern.inputs().get(slot);
        if (snapshot.hasExactInputDomain(pattern.id())
                && pattern.inputs().stream().allMatch(input -> input.alternatives().size() == 1)) {
            AEKey key = compiledSlot.alternatives().get(0).key();
            Input<AEKey> fixed = new Input<>(
                    List.of(new Template<>(key, compiledSlot.templateAmount())), key,
                    snapshot.isEmittable(key), candidate -> new Observation<>(key.equals(candidate), null));
            fixedPatterns.putIfAbsent(pattern.id(), pattern);
            inputs.put(id, fixed);
            return fixed;
        }
        Input<AEKey> captured = server.call(() -> {
            var detail = snapshot.pattern(pattern.id());
            if (detail == null) throw new IllegalStateException("input pattern binding missing");
            var shape = Ae2CompiledPatternFactory.capture(detail, level);
            if (shape == null) throw changed();
            var compiled = shape.compile(pattern.id());
            if (!sameShape(pattern, compiled)) throw changed();
            fingerprints.putIfAbsent(pattern.id(), shape.fingerprint());
            IPatternDetails.IInput original = detail.getInputs()[slot];
            bindings.put(id, original);
            GenericStack[] possible = original.getPossibleInputs();
            List<Template<AEKey>> templates = new ArrayList<>();
            for (var item : possible) templates.add(new Template<>(item.what(), item.amount()));
            var crafting = service.get();
            AEKey encoded = possible[0].what();
            AEKey crafted = selectCrafted(crafting, original, templates);
            return new Input<>(templates, crafted, crafting.canEmitFor(encoded), key -> observe(id, key));
        });
        inputs.put(id, captured);
        return captured;
    }

    private AEKey selectCrafted(ICraftingService crafting, IPatternDetails.IInput input,
            List<Template<AEKey>> possible) {
        AEKey encoded = possible.get(0).key();
        if (!crafting.canEmitFor(encoded) && crafting.getCraftingFor(encoded).isEmpty()) {
            for (var template : possible) {
                if (template.amount() != possible.get(0).amount()) continue;
                AEKey fuzzy = crafting.getFuzzyCraftable(template.key(), key -> input.isValid(key, level));
                if (fuzzy != null) return fuzzy;
            }
        }
        return encoded;
    }

    private Observation<AEKey> observe(Slot slot, AEKey key) {
        Query query = new Query(slot, key);
        Observation<AEKey> cached = observations.get(query);
        if (cached != null) return cached;
        guard.checkpoint(observations.size());
        if (observations.size() >= MAX_OBSERVATIONS) throw new IllegalStateException("input observation limit exceeded");
        Observation<AEKey> value = server.call(() -> read(bindings.get(slot), key));
        observations.put(query, value);
        return value;
    }

    private Observation<AEKey> read(IPatternDetails.IInput input, AEKey key) {
        boolean valid = input.isValid(key, level);
        // AE2 asks for remainders only after accepting a stock template, or for its primary request.
        boolean primary = input.getPossibleInputs()[0].what().equals(key);
        return new Observation<>(valid, valid || primary ? input.getRemainingKey(key) : null);
    }

    void revalidate() {
        // Recheck each used fixed pattern once, not once per slot or candidate.
        validateChunks(fixedPatterns.values(), expected -> {
            var current = Ae2CompiledPatternFactory.capture(snapshot.pattern(expected.id()), level);
            if (current == null || !current.exactInputDomain()
                    || !sameShape(expected, current.compile(expected.id()))
                    || !expected.id().equals(current.fingerprint())) throw changed();
            for (var slot : expected.inputs()) {
                AEKey key = slot.alternatives().get(0).key();
                if (snapshot.isEmittable(key) != service.get().canEmitFor(key)) throw changed();
            }
        });
        validateChunks(fingerprints.entrySet(), entry -> {
            var current = Ae2CompiledPatternFactory.capture(snapshot.pattern(entry.getKey()), level);
            if (current == null || !entry.getValue().equals(current.fingerprint())) throw changed();
        });
        validateChunks(inputs.entrySet().stream()
                .filter(entry -> !fixedPatterns.containsKey(entry.getKey().pattern())).toList(), entry -> {
            var current = snapshot.pattern(entry.getKey().pattern()).getInputs()[entry.getKey().index()];
            var crafting = service.get();
            if (!Objects.equals(entry.getValue().craftedKey(),
                    selectCrafted(crafting, current, entry.getValue().templates()))
                    || entry.getValue().emittable() != crafting.canEmitFor(entry.getValue().templates().get(0).key())) {
                throw changed();
            }
        });
        validateChunks(observations.entrySet(), entry -> {
            Slot slot = entry.getKey().slot();
            var current = snapshot.pattern(slot.pattern()).getInputs()[slot.index()];
            if (!entry.getValue().equals(read(current, entry.getKey().key()))) throw changed();
        });
    }

    private <T> void validateChunks(Iterable<T> values, Consumer<T> check) {
        var iterator = values.iterator();
        int count = 0;
        while (iterator.hasNext()) {
            guard.checkpoint(++count);
            List<T> batch = new ArrayList<>(64);
            while (iterator.hasNext() && batch.size() < 64) batch.add(iterator.next());
            server.call(() -> {
                for (T value : batch) {
                    checkCancelled(0);
                    check.accept(value);
                }
                return null;
            });
        }
    }

    private void checkCancelled(int count) {
        if (owner.isInterrupted() || Thread.currentThread().isInterrupted()) throw new PlanningCancelledException(count);
    }

    private static IllegalStateException changed() {
        return new IllegalStateException("ACO input semantics changed during planning; request a fresh plan");
    }

    static boolean sameShape(CompiledPattern<AEKey> expected, CompiledPattern<AEKey> actual) {
        if (!expected.outputs().equals(actual.outputs()) || expected.externalPush() != actual.externalPush()
                || expected.inputs().size() != actual.inputs().size()) return false;
        for (int i = 0; i < expected.inputs().size(); i++) {
            var a = expected.inputs().get(i);
            var b = actual.inputs().get(i);
            if (a.templateAmount() != b.templateAmount() || !a.alternatives().equals(b.alternatives())) return false;
        }
        return true;
    }

    @Override public List<AEKey> storedFuzzyKeys(AEKey key) { return inventory.fuzzyKeys(key); }
    @Override public KeyIndex<AEKey> newIndex() { return new Ae2KeyIndex(); }

    static final class Ae2KeyIndex implements KeyIndex<AEKey> {
        private final KeyCounter keys = new KeyCounter();
        @Override public void add(AEKey key) { keys.add(key, 0); }
        @Override public List<AEKey> fuzzy(AEKey key) {
            return keys.findFuzzy(key, FuzzyMode.IGNORE_ALL).stream().map(Map.Entry::getKey).toList();
        }
        @Override public List<AEKey> keys() {
            List<AEKey> result = new ArrayList<>();
            for (var entry : keys) result.add(entry.getKey());
            return result;
        }
    }

    private record Slot(String pattern, int index) { }
    private record Query(Slot slot, AEKey key) { }
}

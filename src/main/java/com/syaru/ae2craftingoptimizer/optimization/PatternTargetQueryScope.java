package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** #245: query-local key types only, never wrappers, stock or acceptance results. */
public final class PatternTargetQueryScope implements AutoCloseable {
    private static final ThreadLocal<PatternTargetQueryScope> ACTIVE = new ThreadLocal<>();
    private final PatternTargetQueryScope previous;
    private final Set<AEKeyType> required = Collections.newSetFromMap(new IdentityHashMap<>());
    private Object owner;

    private PatternTargetQueryScope(KeyCounter[] inputs, Set<AEKey> patternInputs) {
        previous = ACTIVE.get();
        for (var slot : inputs) for (var entry : slot)
            if (entry.getLongValue() > 0) required.add(entry.getKey().getType());
        // Blocking checks may examine inputs advertised by other patterns on this provider.
        for (var key : patternInputs) required.add(key.getType());
        ACTIVE.set(this);
    }

    public static PatternTargetQueryScope open(KeyCounter[] inputs, Set<AEKey> patternInputs) {
        return new PatternTargetQueryScope(inputs, patternInputs);
    }

    public static void bind(Object cache) {
        var scope = ACTIVE.get();
        if (scope != null && scope.owner == null) scope.owner = cache;
    }

    public static boolean needs(Object cache, Map<AEKeyType, ExternalStorageStrategy> strategies,
            ExternalStorageStrategy candidate) {
        var scope = ACTIVE.get();
        if (scope == null || scope.owner != cache || scope.required.isEmpty()) return true;
        for (var entry : strategies.entrySet())
            if (entry.getValue() == candidate && scope.required.contains(entry.getKey())) return true;
        return false;
    }

    @Override public void close() {
        if (ACTIVE.get() != this) throw new IllegalStateException("Unbalanced pattern target query scope");
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
    }
}

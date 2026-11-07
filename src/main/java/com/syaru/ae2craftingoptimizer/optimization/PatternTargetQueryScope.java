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
    private AEKeyType firstType;
    private AEKeyType secondType;
    private Set<AEKeyType> additionalTypes;
    private Object owner;

    private PatternTargetQueryScope(KeyCounter[] inputs, Set<AEKey> patternInputs) {
        previous = ACTIVE.get();
        for (var slot : inputs) for (var entry : slot)
            if (entry.getLongValue() > 0) addRequired(entry.getKey().getType());
        // Blocking checks may examine inputs advertised by other patterns on this provider.
        for (var key : patternInputs) addRequired(key.getType());
        ACTIVE.set(this);
    }

    private void addRequired(AEKeyType type) {
        if (type == null || type == firstType || type == secondType) return;
        if (firstType == null) {
            firstType = type;
        } else if (secondType == null) {
            secondType = type;
        } else {
            if (additionalTypes == null) {
                additionalTypes = Collections.newSetFromMap(new IdentityHashMap<>(4));
            }
            additionalTypes.add(type);
        }
    }

    private boolean isRequired(AEKeyType type) {
        return type != null && (type == firstType || type == secondType
                || (additionalTypes != null && additionalTypes.contains(type)));
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
        if (scope == null || scope.owner != cache || scope.firstType == null) return true;
        for (var entry : strategies.entrySet())
            if (entry.getValue() == candidate && scope.isRequired(entry.getKey())) return true;
        return false;
    }

    @Override public void close() {
        if (ACTIVE.get() != this) throw new IllegalStateException("Unbalanced pattern target query scope");
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
    }
}

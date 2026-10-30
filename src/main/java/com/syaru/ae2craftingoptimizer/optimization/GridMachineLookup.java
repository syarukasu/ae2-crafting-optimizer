package com.syaru.ae2craftingoptimizer.optimization;

import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** #245: immutable owner sets only; native topology mutations invalidate all reads. */
public final class GridMachineLookup {
    private final Map<Class<?>, Set<?>> owners = new IdentityHashMap<>();
    private final Map<Class<?>, Boolean> identityTypes = new IdentityHashMap<>();
    private final Set<Class<?>> pending = Collections.newSetFromMap(new IdentityHashMap<>());

    public Set<?> cached(Class<?> type) {
        if (!identityTypes.computeIfAbsent(type, GridMachineLookup::usesIdentity)) return null;
        var result = owners.get(type);
        if (result == null) pending.add(type);
        return result;
    }

    public void remember(Class<?> type, Set<?> value) {
        if (!pending.remove(type) || !(value instanceof ImmutableSet<?>)) return;
        for (var owner : value) if (owner.getClass() != type) return;
        owners.put(type, value);
    }

    public void invalidate() {
        owners.clear();
        pending.clear();
    }

    private static boolean usesIdentity(Class<?> type) {
        try {
            return type.getMethod("equals", Object.class).getDeclaringClass() == Object.class
                    && type.getMethod("hashCode").getDeclaringClass() == Object.class;
        } catch (NoSuchMethodException notAnOwnerClass) { return false; }
    }
}


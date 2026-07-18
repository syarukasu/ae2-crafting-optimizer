package com.syaru.ae2craftingoptimizer.api.big;

import appeng.api.stacks.AEKey;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.WeakHashMap;

/** Weak owner-to-runtime registry for optional CPU add-ons. */
public final class BigCraftingHostRegistry {
    private static final Map<Object, BigCraftingHostRuntime<AEKey>> HOSTS = new WeakHashMap<>();

    private BigCraftingHostRegistry() {
    }

    public static synchronized void register(Object owner, BigCraftingHostRuntime<AEKey> runtime) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(runtime, "runtime");
        BigCraftingHostRuntime<AEKey> existing = HOSTS.putIfAbsent(owner, runtime);
        if (existing != null && existing != runtime) {
            throw new IllegalStateException("crafting host owner is already registered");
        }
    }

    public static synchronized Optional<BigCraftingHostRuntime<AEKey>> find(Object owner) {
        return Optional.ofNullable(HOSTS.get(Objects.requireNonNull(owner, "owner")));
    }

    /** Server-tick integrations iterate an immutable snapshot so weak-map cleanup cannot race the loop. */
    public static synchronized Map<Object, BigCraftingHostRuntime<AEKey>> snapshot() {
        return Map.copyOf(new LinkedHashMap<>(HOSTS));
    }

    public static synchronized void unregister(Object owner) {
        HOSTS.remove(Objects.requireNonNull(owner, "owner"));
    }

    public static synchronized void clear() {
        HOSTS.clear();
    }
}

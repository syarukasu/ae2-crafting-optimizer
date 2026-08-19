package com.syaru.ae2craftingoptimizer.api.contract;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** One-shot owner of the immutable integration handshake snapshot. */
public final class IntegrationCapabilitiesRegistry {
    private static final AtomicReference<IntegrationCapabilities> SNAPSHOT = new AtomicReference<>();

    private IntegrationCapabilitiesRegistry() {
    }

    public static void initializeOnce(IntegrationCapabilities capabilities) {
        Objects.requireNonNull(capabilities, "capabilities");
        if (!SNAPSHOT.compareAndSet(null, capabilities)) {
            throw new IllegalStateException("integration capabilities were already initialized");
        }
    }

    public static Optional<IntegrationCapabilities> peek() {
        return Optional.ofNullable(SNAPSHOT.get());
    }

    public static IntegrationCapabilities snapshot() {
        IntegrationCapabilities capabilities = SNAPSHOT.get();
        if (capabilities == null) {
            throw new IllegalStateException("integration capabilities are not initialized");
        }
        return capabilities;
    }
}

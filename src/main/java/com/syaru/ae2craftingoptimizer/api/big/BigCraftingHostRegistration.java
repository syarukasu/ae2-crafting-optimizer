package com.syaru.ae2craftingoptimizer.api.big;

import java.util.UUID;

/** Explicit lifecycle handle for one owner/runtime registration. */
public interface BigCraftingHostRegistration extends AutoCloseable {
    Object ownerIdentity();
    UUID runtimeIdentity();
    long generation();
    boolean isClosed();
    BigCraftingHostSnapshot snapshot(long revision, BigCraftingHostBackendState backendState);

    /** Registers controller cleanup that runs exactly once when this generation closes. */
    void onClose(Runnable cleanup);

    /** Idempotent; an old generation cannot close a newer registration. */
    @Override
    void close();
}

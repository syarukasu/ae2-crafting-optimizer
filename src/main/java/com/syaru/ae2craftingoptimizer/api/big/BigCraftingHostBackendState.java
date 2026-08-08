package com.syaru.ae2craftingoptimizer.api.big;

/** Stable state labels used by an atomic Big Crafting Host snapshot. */
public enum BigCraftingHostBackendState {
    ACTIVE,
    PAUSED,
    DEGRADED,
    CLOSING,
    CLOSED,
    UNAVAILABLE
}

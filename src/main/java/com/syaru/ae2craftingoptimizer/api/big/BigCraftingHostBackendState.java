package com.syaru.ae2craftingoptimizer.api.big;

/** BigInteger Hostの原子的な状態表示に使う安定ラベル。 */
public enum BigCraftingHostBackendState {
    ACTIVE,
    PAUSED,
    DEGRADED,
    CLOSING,
    CLOSED,
    UNAVAILABLE
}

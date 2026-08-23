package com.syaru.ae2craftingoptimizer.optimization;

/** キャッシュまたは所有状態を失効させる正本イベント。 */
public enum OptimizationInvalidation {
    SERVER_LIFECYCLE,
    SERVER_TICK,
    STORAGE_GENERATION,
    PROVIDER_GENERATION,
    TOPOLOGY_GENERATION,
    RESOURCE_RELOAD,
    PLANNING_COMPLETION,
    TRANSACTION_TERMINAL_STATE,
    EXTERNAL_OWNER
}

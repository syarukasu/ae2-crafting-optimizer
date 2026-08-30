package com.syaru.ae2craftingoptimizer.access;

/** CraftingService内の保留Provider更新を、計算Snapshot取得前に確定する境界。 */
public interface CraftingProviderRefreshAccess {
    void aco$flushPendingProviderRefreshes();
}

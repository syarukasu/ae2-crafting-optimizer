package com.syaru.ae2craftingoptimizer.optimization;

/** AE2標準経路へ安全に戻せる最終地点。 */
public enum FallbackBoundary {
    BEFORE_READ,
    BEFORE_MUTATION,
    BEFORE_OWNERSHIP,
    NEVER_AFTER_OWNERSHIP
}


package com.syaru.ae2craftingoptimizer.optimization;

/**
 * ACOが最適化する責務領域。
 *
 * <p>Issue #129の回帰防止として、個別機能をこの境界へ必ず所属させる。
 * 一つの領域を無効化した場合、その領域はAE2の状態へ一切介入しない。
 */
public enum OptimizationDomain {
    NETWORK_TOPOLOGY,
    STORAGE_IO,
    PATTERN_PROVIDER,
    CLIENT_SYNC,
    CRAFTING_PLANNING,
    CRAFTING_EXECUTION,
    BIG_INTEGER,
    OPTIONAL_INTEGRATION
}


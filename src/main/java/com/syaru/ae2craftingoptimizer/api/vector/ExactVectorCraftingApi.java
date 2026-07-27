package com.syaru.ae2craftingoptimizer.api.vector;

/** ACO Exact Vector Craftingの、既存BigInteger APIとは独立した公開契約版。 */
public final class ExactVectorCraftingApi {
    public static final int API_VERSION = 1;
    /** AE電力を整数で分配するため、一AEを百万micro-AEとして扱う。 */
    public static final long MICRO_AE_PER_AE = 1_000_000L;

    private ExactVectorCraftingApi() {
    }
}

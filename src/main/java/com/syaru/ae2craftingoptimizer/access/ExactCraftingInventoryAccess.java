package com.syaru.ae2craftingoptimizer.access;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.Map;

/**
 * AE2のListCraftingInventoryへBigInteger数量を保持させる拡張契約。
 *
 * <p>通常Jobでは無効のまま既存long実装を使用する。Exact JobのwaitingForだけが有効化し、
 * 既存KeyCounterには0..Long.MAX_VALUEの互換投影を保持する。</p>
 */
public interface ExactCraftingInventoryAccess {
    boolean aco$hasExactCounts();

    void aco$replaceExactCounts(Map<AEKey, BigInteger> counts);

    Map<AEKey, BigInteger> aco$getExactCounts();

    BigInteger aco$getExactCount(AEKey key);
}

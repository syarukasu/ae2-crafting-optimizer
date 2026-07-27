package com.syaru.ae2craftingoptimizer.api.vector;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;

/**
 * Infinity BigInteger Cellの派生実装が、ACOの直接BigInteger挿入へ追加制約を伝える契約。
 *
 * <p>この契約を実装しない未知の派生セルへ、ACOはlongを超える量を直接挿入しない。</p>
 */
public interface ExactVectorStoragePolicy {
    /**
     * 現在量から追加できる最大量を返す。
     *
     * @return 0以上の正確な受入可能量
     */
    BigInteger acoMaximumExactInsert(AEKey key, BigInteger currentAmount);
}

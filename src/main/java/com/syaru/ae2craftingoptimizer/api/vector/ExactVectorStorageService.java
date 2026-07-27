package com.syaru.ae2craftingoptimizer.api.vector;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.integration.ExactNetworkStorageBridge;
import java.math.BigInteger;

/**
 * Exact Vector Executorが使用する、数量分割を行わないBigInteger在庫境界。
 *
 * <p>現時点ではACOが監査したExtendedAE Plus Infinity BigInteger Cellだけを対象にする。</p>
 */
public final class ExactVectorStorageService {
    private ExactVectorStorageService() {
    }

    public static boolean canExtract(
            IGrid grid,
            AEKey key,
            BigInteger amount,
            IActionSource source) {
        return ExactNetworkStorageBridge.canExtract(grid, key, amount, source);
    }

    public static ExactStorageMutationResult extract(
            IGrid grid,
            AEKey key,
            BigInteger amount,
            IActionSource source) {
        return ExactNetworkStorageBridge.extract(grid, key, amount, source);
    }

    public static boolean canInsert(
            IGrid grid,
            AEKey key,
            BigInteger amount,
            IActionSource source) {
        return ExactNetworkStorageBridge.canInsert(grid, key, amount, source);
    }

    public static ExactStorageMutationResult insert(
            IGrid grid,
            AEKey key,
            BigInteger amount,
            IActionSource source) {
        return ExactNetworkStorageBridge.insert(grid, key, amount, source);
    }
}

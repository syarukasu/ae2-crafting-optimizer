package com.syaru.ae2craftingoptimizer.api.vector;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.integration.ExactNetworkStorageBridge;
import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    public static boolean canExtractAll(
            IGrid grid,
            Map<AEKey, BigInteger> amounts,
            IActionSource source) {
        return ExactNetworkStorageBridge.canExtractAll(
                grid,
                amounts,
                source);
    }

    public static ExactStorageMutationResult extractAll(
            IGrid grid,
            Map<AEKey, BigInteger> amounts,
            IActionSource source) {
        return ExactNetworkStorageBridge.extractAll(
                grid,
                amounts,
                source);
    }

    public static boolean canInsertAll(
            IGrid grid,
            Map<AEKey, BigInteger> amounts,
            IActionSource source) {
        return ExactNetworkStorageBridge.canInsertAll(
                grid,
                amounts,
                source);
    }

    public static ExactStorageMutationResult insertAll(
            IGrid grid,
            Map<AEKey, BigInteger> amounts,
            IActionSource source) {
        return ExactNetworkStorageBridge.insertAll(
                grid,
                amounts,
                source);
    }

    /** 停止復旧用に、監査済みBigIntegerセルだけの正確な現在量を読む。 */
    public static Optional<BigInteger> exactStoredAmount(
            IGrid grid,
            AEKey key) {
        return ExactNetworkStorageBridge.exactStoredAmount(
                grid,
                key);
    }

    /** 停止復旧用に、指定された全キーを一つのmount走査で読む。 */
    public static Optional<Map<AEKey, BigInteger>> exactStoredAmounts(
            IGrid grid,
            Set<AEKey> keys) {
        return ExactNetworkStorageBridge.exactStoredAmounts(
                grid,
                keys);
    }
}

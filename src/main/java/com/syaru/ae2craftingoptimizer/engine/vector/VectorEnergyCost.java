package com.syaru.ae2craftingoptimizer.engine.vector;

import java.math.BigInteger;
import java.util.Objects;

/** Exact Vectorの電力を注文数量ではなく固有Patternノード数から計算する。 */
public final class VectorEnergyCost {
    private VectorEnergyCost() {
    }

    public static BigInteger forPatternNodes(
            int patternNodeCount,
            BigInteger energyMicroAePerPatternNode,
            int maximumBits) {
        Objects.requireNonNull(
                energyMicroAePerPatternNode,
                "energyMicroAePerPatternNode");
        /*
         * Patternが一件もない計画はVector実行対象ではない。
         * 負電力と無効な桁上限も、設備APIへ渡す前に明示的に拒否する。
         */
        if (patternNodeCount <= 0
                || energyMicroAePerPatternNode.signum() < 0
                || maximumBits <= 0) {
            throw new IllegalArgumentException(
                    "invalid Pattern-node energy inputs");
        }

        BigInteger result = energyMicroAePerPatternNode.multiply(
                BigInteger.valueOf(patternNodeCount));
        // Config上限を超える電力値は切り詰めず、計画全体を安全に拒否する。
        if (result.bitLength() > maximumBits) {
            throw new ArithmeticException(
                    "Vector energy exceeds ACO BigInteger limit");
        }
        return result;
    }
}

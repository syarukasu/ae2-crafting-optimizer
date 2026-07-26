package com.syaru.ae2craftingoptimizer.api.vector;

import java.math.BigInteger;
import java.util.Objects;

/** 総micro-AEをactive tickへ余りなく分配する整数Schedule。 */
public record VectorEnergySchedule(
        BigInteger totalMicroAe,
        int totalTicks,
        BigInteger basePerTick,
        int remainderTicks) {
    public VectorEnergySchedule {
        Objects.requireNonNull(totalMicroAe, "totalMicroAe");
        Objects.requireNonNull(basePerTick, "basePerTick");
        if (totalMicroAe.signum() < 0
                || totalTicks <= 0
                || basePerTick.signum() < 0
                || remainderTicks < 0
                || remainderTicks >= totalTicks) {
            throw new IllegalArgumentException("invalid vector energy schedule");
        }
        BigInteger reconstructed = basePerTick
                .multiply(BigInteger.valueOf(totalTicks))
                .add(BigInteger.valueOf(remainderTicks));
        if (!reconstructed.equals(totalMicroAe)) {
            throw new IllegalArgumentException(
                    "vector energy schedule does not conserve total energy");
        }
    }

    public static VectorEnergySchedule divide(
            BigInteger totalMicroAe,
            int totalTicks) {
        Objects.requireNonNull(totalMicroAe, "totalMicroAe");
        if (totalMicroAe.signum() < 0 || totalTicks <= 0) {
            throw new IllegalArgumentException(
                    "energy and tick count must not be negative");
        }
        BigInteger[] quotientAndRemainder = totalMicroAe.divideAndRemainder(
                BigInteger.valueOf(totalTicks));
        return new VectorEnergySchedule(
                totalMicroAe,
                totalTicks,
                quotientAndRemainder[0],
                quotientAndRemainder[1].intValueExact());
    }

    public BigInteger microAeForTick(int activeTick) {
        if (activeTick < 0 || activeTick >= totalTicks) {
            throw new IndexOutOfBoundsException(activeTick);
        }
        // 割り切れない余りは先頭tickへ一micro-AEずつ配る。
        return activeTick < remainderTicks
                ? basePerTick.add(BigInteger.ONE)
                : basePerTick;
    }
}

package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.math.BigInteger;
import java.util.Map;

/** MinecraftのConfigロードなしで、実コンストラクタを検証するテスト専用境界。 */
public final class BigIntegerSimulationPlanTestFactory {
    private BigIntegerSimulationPlanTestFactory() {
    }

    public static BigIntegerSimulationPlan create(
            GenericStack finalOutput,
            BigCraftingPlan<AEKey> exactPlan,
            Map<IPatternDetails, BigInteger> exactPatternTimes,
            BigInteger exactBytes,
            int maximumBits) {
        return new BigIntegerSimulationPlan(
                finalOutput,
                exactPlan,
                exactPatternTimes,
                exactBytes,
                maximumBits);
    }
}

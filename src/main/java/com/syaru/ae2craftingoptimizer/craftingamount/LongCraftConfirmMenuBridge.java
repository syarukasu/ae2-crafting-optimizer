package com.syaru.ae2craftingoptimizer.craftingamount;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.AEKey;

/**
 * CraftConfirmMenuのintフィールドを変更せず、longルート量をSidecarで保持する。
 */
public interface LongCraftConfirmMenuBridge {
    boolean aco$planExact(AEKey what, java.math.BigInteger amount, CalculationStrategy strategy);

    boolean aco$planLong(
            AEKey what,
            long amount,
            CalculationStrategy strategy);

    long aco$getLongRootAmount();
}

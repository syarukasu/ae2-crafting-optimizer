package com.syaru.ae2craftingoptimizer.craftingamount;

import appeng.api.stacks.AEKey;

/**
 * CraftAmountMenuへ追加するlong専用の狭い境界。
 * int以下の注文はこのAPIを通らず、AE2本来のconfirm(int, ...)を使う。
 */
public interface LongCraftAmountMenuBridge {
    void aco$confirmLong(long amount, boolean subtractStoredAmount, boolean autoStart);

    void aco$setWhatToCraftLong(AEKey what, long amount);

    void aco$setClientInitialAmount(long amount);

    long aco$getInitialAmount();
}

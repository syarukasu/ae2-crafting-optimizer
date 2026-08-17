package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.networking.crafting.ICraftingPlan;
import java.math.BigInteger;

/**
 * AE2のsigned long APIだけでは表現できない真値を持つACO内部計画。
 *
 * <p>この型をAE2や他MODへ直接渡してはいけない。外部境界では必ず
 * {@link Ae2CraftingPlanSidecars#expose(WideCraftingPlan)}が作る純正CraftingPlanを使用する。</p>
 */
public interface WideCraftingPlan extends ICraftingPlan {
    /**
     * CPU容量台帳へ渡す真のbyte数。
     *
     * <p>{@link #bytes()}はAE2互換の飽和値なので、診断や受理判定へ使ってはいけない。</p>
     */
    BigInteger exactBytes();
}

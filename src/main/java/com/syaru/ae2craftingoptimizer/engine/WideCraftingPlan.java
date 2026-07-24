package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.networking.crafting.ICraftingPlan;

/**
 * AE2のsigned long APIだけでは表現できない真値を持つACO内部計画。
 *
 * <p>この型をAE2や他MODへ直接渡してはいけない。外部境界では必ず
 * {@link Ae2CraftingPlanSidecars#expose(WideCraftingPlan)}が作る純正CraftingPlanを使用する。</p>
 */
public interface WideCraftingPlan extends ICraftingPlan {
}

package com.syaru.ae2craftingoptimizer.access;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.Map;

/**
 * Compiled Crafting Islandが必要とする、実行Jobの最小会計Accessor。
 */
public interface CraftingIslandJobAccess {
    Map<IPatternDetails, Object> aco$getIslandTasks();

    GenericStack aco$getIslandFinalOutput();

    long aco$getIslandRemainingAmount();

    boolean aco$canAcceptIslandOutput(AEKey key, long amount);

    void aco$stageIslandOutput(AEKey key, long amount);

    void aco$unstageIslandOutput(AEKey key, long amount);

    void aco$decrementIslandInternalOutput(AEKey key, long amount);

    void aco$setIslandSuspended(boolean suspended);
}

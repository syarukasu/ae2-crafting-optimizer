package com.syaru.ae2craftingoptimizer.access;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.engine.BigIntegerCraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.ExactCraftingJobState;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/** Advanced AE実JobへBigInteger正本を設置・同期するためのversion-pinned契約。 */
public interface AdvancedAeExactCraftingJobAccess {
    void aco$installExactState(BigIntegerCraftingPlan plan);

    void aco$loadExactState(CompoundTag owner);

    void aco$writeExactState(CompoundTag owner);

    boolean aco$isExactJob();

    @Nullable
    ExactCraftingJobState aco$getExactState();

    void aco$reconcileExactAccounting(
            Map<AEItemKey, BigInteger> dispatchedTasks,
            Map<AEKey, BigInteger> introducedOutputs,
            Map<AEKey, BigInteger> creditedOutputs,
            BigInteger remainingOutput);

    Map<AEItemKey, BigInteger> aco$getExactRemainingTasks();

    Map<AEKey, BigInteger> aco$getExactWaitingFor();

    BigInteger aco$getExactRemainingOutput();

    boolean aco$isExactAccountingBalanced();
}

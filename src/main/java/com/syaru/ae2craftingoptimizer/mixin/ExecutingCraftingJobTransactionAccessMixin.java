package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import com.syaru.ae2craftingoptimizer.access.CraftingJobTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingTaskProgressAccess;
import com.syaru.ae2craftingoptimizer.access.ExactCraftingInventoryAccess;
import com.syaru.ae2craftingoptimizer.access.ExactCraftingJobAccess;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.BigIntegerCraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.ExactCraftingJobLedger;
import com.syaru.ae2craftingoptimizer.engine.ExactCraftingJobState;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** 標準AE2 Jobの通常取引Accessorと、Issue #115のexact Sidecarを同じ実Jobへ公開する。 */
@Mixin(value = ExecutingCraftingJob.class, remap = false)
public abstract class ExecutingCraftingJobTransactionAccessMixin
        implements CraftingJobTransactionAccess, ExactCraftingJobAccess {
    @Unique
    private static final String ACO_EXACT_JOB_NBT = "acoExactCraftingJob";

    @Shadow
    @Final
    private Map<IPatternDetails, Object> tasks;

    @Shadow
    @Final
    private ListCraftingInventory waitingFor;

    @Shadow
    @Final
    private CraftingLink link;

    @Shadow
    private long remainingAmount;

    /** nullは通常AE2 Job、非nullは同じ実Jobへ付随するBigInteger正本。 */
    @Unique
    private ExactCraftingJobState aco$exactState;

    /** AE2のremainingAmountをBigIntegerへ拡張した、同じ実Job上の正確な残数。 */
    @Unique
    private BigInteger aco$exactRemainingAmount;

    @Override
    public Map<IPatternDetails, Object> aco$getTasks() {
        return tasks;
    }

    @Override
    public ICraftingInventory aco$getWaitingFor() {
        return waitingFor;
    }

    @Override
    public long aco$getWaitingForAmount(AEKey key) {
        return waitingFor.list.get(key);
    }

    @Override
    public UUID aco$getCraftingJobId() {
        return link.getCraftingID();
    }

    @Override
    public void aco$installExactState(BigIntegerCraftingPlan plan) {
        if (aco$exactState != null) {
            throw new IllegalStateException("AE2 job already has exact accounting");
        }
        aco$exactState = ExactCraftingJobState.fromPlan(plan);
        aco$applyExactRuntimeCounters();
    }

    @Override
    public void aco$loadExactState(
            CompoundTag owner,
            HolderLookup.Provider registries) {
        // exactタグが無い通常JobはSidecarを作らず、AE2本来のlong会計だけを使う。
        if (!owner.contains(ACO_EXACT_JOB_NBT, Tag.TAG_COMPOUND)) {
            aco$exactState = null;
            return;
        }
        aco$exactState = ExactCraftingJobState.load(
                owner.getCompound(ACO_EXACT_JOB_NBT),
                ACOConfig.getBigIntegerMaximumBits(),
                registries);
        aco$applyExactRuntimeCounters();
    }

    @Override
    public void aco$writeExactState(
            CompoundTag owner,
            HolderLookup.Provider registries) {
        if (aco$exactState == null) {
            return;
        }
        /* 保存前に実カウンタとReceipt Journalの完全一致だけを検証し、値を補正しない。 */
        aco$exactState.verifyRuntimeCounters(
                aco$getExactRemainingTasks(),
                aco$getExactWaitingFor(),
                aco$getExactRemainingOutput());
        owner.put(
                ACO_EXACT_JOB_NBT,
                aco$exactState.save(
                        ACOConfig.getBigIntegerMaximumBits(),
                        registries));
    }

    @Override
    public boolean aco$isExactJob() {
        return aco$exactState != null;
    }

    @Override
    public ExactCraftingJobState aco$getExactState() {
        return aco$exactState;
    }

    @Override
    public void aco$reconcileExactAccounting(
            Map<AEItemKey, BigInteger> dispatchedTasks,
            Map<AEKey, BigInteger> introducedOutputs,
            Map<AEKey, BigInteger> creditedOutputs,
            BigInteger remainingOutput) {
        ExactCraftingJobState state = aco$requireExactState();
        state.reconcile(dispatchedTasks, introducedOutputs, creditedOutputs, remainingOutput);
        aco$applyExactRuntimeCounters();
    }

    @Override
    public Map<AEItemKey, BigInteger> aco$getExactRemainingTasks() {
        aco$requireExactState();
        Map<AEItemKey, BigInteger> remaining = new LinkedHashMap<>();
        // 実Jobの全TaskProgressをPattern定義単位へまとめ、正確な残タスク数を得る。
        for (var entry : tasks.entrySet()) {
            if (!(entry.getValue() instanceof CraftingTaskProgressAccess progress)) {
                throw new IllegalStateException("AE2 task progress access is missing");
            }
            BigInteger amount = progress.aco$getExactTaskProgress();
            if (amount.signum() < 0) {
                throw new IllegalStateException("AE2 exact task progress is negative");
            }
            if (amount.signum() > 0) {
                remaining.merge(entry.getKey().getDefinition(), amount, BigInteger::add);
            }
        }
        return Map.copyOf(remaining);
    }

    @Override
    public Map<AEKey, BigInteger> aco$getExactWaitingFor() {
        aco$requireExactState();
        if (!(waitingFor instanceof ExactCraftingInventoryAccess exactInventory)
                || !exactInventory.aco$hasExactCounts()) {
            throw new IllegalStateException("AE2 waitingFor has no exact accounting");
        }
        return exactInventory.aco$getExactCounts();
    }

    @Override
    public BigInteger aco$getExactRemainingOutput() {
        aco$requireExactState();
        if (aco$exactRemainingAmount == null) {
            throw new IllegalStateException("AE2 final output has no exact accounting");
        }
        return aco$exactRemainingAmount;
    }

    @Override
    public boolean aco$isExactAccountingBalanced() {
        return aco$getExactRemainingTasks().isEmpty()
                && aco$getExactWaitingFor().isEmpty()
                && aco$getExactRemainingOutput().signum() == 0;
    }

    @Unique
    private ExactCraftingJobState aco$requireExactState() {
        if (aco$exactState == null) {
            throw new IllegalStateException("AE2 job has no exact accounting");
        }
        return aco$exactState;
    }

    @Unique
    private void aco$applyExactRuntimeCounters() {
        ExactCraftingJobState state = aco$requireExactState();
        Map<AEItemKey, BigInteger> remaining = state.remainingTasks();
        Map<AEItemKey, CraftingTaskProgressAccess> taskCounters = new LinkedHashMap<>();
        /* 第一巡目では値を変更せず、全TaskProgressとPattern定義の一対一対応を証明する。 */
        for (var entry : tasks.entrySet()) {
            AEItemKey definition = entry.getKey().getDefinition();
            if (!(entry.getValue() instanceof CraftingTaskProgressAccess progress)) {
                throw new IllegalStateException("AE2 task progress access is missing");
            }
            if (taskCounters.putIfAbsent(definition, progress) != null) {
                throw new IllegalStateException(
                        "AE2 exact job contains duplicate pattern definitions");
            }
        }
        if (!taskCounters.keySet().equals(state.taskTotals().keySet())) {
            throw new IllegalStateException(
                    "exact task definitions do not match the AE2 job");
        }
        if (!(waitingFor instanceof ExactCraftingInventoryAccess exactInventory)) {
            throw new IllegalStateException("AE2 waitingFor exact mixin is missing");
        }
        // 第二巡目で、BigInteger正本とAE2向け飽和long Facadeを同じTaskへ設置する。
        for (var entry : taskCounters.entrySet()) {
            entry.getValue().aco$setExactTaskProgress(
                    remaining.getOrDefault(entry.getKey(), BigInteger.ZERO));
        }
        aco$exactRemainingAmount = state.remainingOutput();
        remainingAmount = ExactCraftingJobLedger.saturatedLong(aco$exactRemainingAmount);
        exactInventory.aco$replaceExactCounts(state.waitingFor());
    }
}

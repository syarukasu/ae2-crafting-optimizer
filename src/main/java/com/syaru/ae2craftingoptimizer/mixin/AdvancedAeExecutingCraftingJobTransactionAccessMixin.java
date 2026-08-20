package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import com.syaru.ae2craftingoptimizer.access.AdvancedAeExactCraftingJobAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingJobTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingTaskProgressAccess;
import com.syaru.ae2craftingoptimizer.access.ExactCraftingInventoryAccess;
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
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Advanced AEの実Job会計をV2 Pattern Batchへ公開する最小Accessor。
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob", remap = false)
public abstract class AdvancedAeExecutingCraftingJobTransactionAccessMixin
        implements CraftingJobTransactionAccess, AdvancedAeExactCraftingJobAccess {
    @Unique
    private static final String ACO_EXACT_JOB_NBT =
            "acoExactCraftingJob";

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

    /** nullは通常Advanced AE Job、非nullは同じ実Jobへ付随するBigInteger正本。 */
    @Unique
    private ExactCraftingJobState aco$exactState;

    /** Advanced AEのremainingAmountをBigIntegerへ拡張した、同じ実Job上の正確な残数。 */
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
    public void aco$installExactState(
            BigIntegerCraftingPlan plan) {
        if (aco$exactState != null) {
            throw new IllegalStateException(
                    "Advanced AE job already has exact accounting");
        }
        aco$exactState = ExactCraftingJobState.fromPlan(plan);
        aco$applyExactRuntimeCounters();
    }

    @Override
    public void aco$loadExactState(
            CompoundTag owner,
            HolderLookup.Provider registries) {
        if (!owner.contains(
                ACO_EXACT_JOB_NBT,
                Tag.TAG_COMPOUND)) {
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
        /*
         * 実行時の正本はTaskProgress、waitingFor、remainingAmount拡張である。
         * 保存前に永続Journalとの完全一致だけを検証し、どちらの値も補正しない。
         */
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
        state.reconcile(
                dispatchedTasks,
                introducedOutputs,
                creditedOutputs,
                remainingOutput);
        aco$applyExactRuntimeCounters();
    }

    @Override
    public Map<AEItemKey, BigInteger> aco$getExactRemainingTasks() {
        aco$requireExactState();
        Map<AEItemKey, BigInteger> remaining =
                new LinkedHashMap<>();
        // 実Jobの各TaskProgressを定義Item単位へまとめ、正確な残タスク数として返す。
        for (var entry : tasks.entrySet()) {
            if (!(entry.getValue()
                    instanceof CraftingTaskProgressAccess progress)) {
                throw new IllegalStateException(
                        "Advanced AE task progress access is missing");
            }
            BigInteger amount =
                    progress.aco$getExactTaskProgress();
            if (amount.signum() < 0) {
                throw new IllegalStateException(
                        "Advanced AE exact task progress is negative");
            }
            if (amount.signum() > 0) {
                remaining.merge(
                        entry.getKey().getDefinition(),
                        amount,
                        BigInteger::add);
            }
        }
        return Map.copyOf(remaining);
    }

    @Override
    public Map<AEKey, BigInteger> aco$getExactWaitingFor() {
        aco$requireExactState();
        if (!(waitingFor
                instanceof ExactCraftingInventoryAccess exactInventory)
                || !exactInventory.aco$hasExactCounts()) {
            throw new IllegalStateException(
                    "Advanced AE waitingFor has no exact accounting");
        }
        return exactInventory.aco$getExactCounts();
    }

    @Override
    public BigInteger aco$getExactRemainingOutput() {
        aco$requireExactState();
        if (aco$exactRemainingAmount == null) {
            throw new IllegalStateException(
                    "Advanced AE final output has no exact accounting");
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
            throw new IllegalStateException(
                    "Advanced AE job has no exact accounting");
        }
        return aco$exactState;
    }

    @Unique
    private void aco$applyExactRuntimeCounters() {
        ExactCraftingJobState state = aco$requireExactState();
        Map<AEItemKey, BigInteger> remaining =
                state.remainingTasks();
        Map<AEItemKey, CraftingTaskProgressAccess> taskCounters =
                new LinkedHashMap<>();
        /*
         * 第一巡目では実カウンタを一切変更せず、全TaskProgressとPattern定義の一対一対応を証明する。
         * Map走査順や再起動後のPatternインスタンス同一性には依存しない。
         */
        for (var entry : tasks.entrySet()) {
            AEItemKey definition = entry.getKey().getDefinition();
            // TaskProgress Mixinが欠けるJobは、一件も変更する前に拒否する。
            if (!(entry.getValue()
                    instanceof CraftingTaskProgressAccess progress)) {
                throw new IllegalStateException(
                        "Advanced AE task progress access is missing");
            }
            // 一つの定義を二つのTaskProgressへ重複投影すると残数が倍になるため拒否する。
            if (taskCounters.putIfAbsent(
                            definition,
                            progress)
                    != null) {
                throw new IllegalStateException(
                        "Advanced AE exact job contains duplicate pattern definitions");
            }
        }
        // 初期タスク集合まで完全一致させ、完了済み0タスクの欠落も見逃さない。
        if (!taskCounters.keySet().equals(
                state.taskTotals().keySet())) {
            throw new IllegalStateException(
                    "exact task definitions do not match the Advanced AE job");
        }

        // waitingFor MixinもTaskProgress変更前に検証し、部分反映を作らない。
        if (!(waitingFor
                instanceof ExactCraftingInventoryAccess exactInventory)) {
            throw new IllegalStateException(
                    "Advanced AE waitingFor exact mixin is missing");
        }
        /*
         * 第二巡目で、同じ実TaskProgressへBigInteger正確値と飽和long互換値を同時に設置する。
         * 完了済みTaskも0へ更新するため、初期タスク集合を全件走査する。
         */
        for (var entry : taskCounters.entrySet()) {
            entry.getValue().aco$setExactTaskProgress(
                    remaining.getOrDefault(
                            entry.getKey(),
                            BigInteger.ZERO));
        }
        /*
         * Listener通知が例外になっても三つの実会計値が食い違わないよう、
         * final-output正確値をwaitingFor差分通知より先に確定する。
         */
        aco$exactRemainingAmount =
                state.remainingOutput();
        remainingAmount = ExactCraftingJobLedger.saturatedLong(
                aco$exactRemainingAmount);
        // 同じListCraftingInventoryへ正確な待機量を設置し、内部KeyCounterだけをlong投影する。
        exactInventory.aco$replaceExactCounts(
                state.waitingFor());
    }
}

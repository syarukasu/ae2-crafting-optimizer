package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import com.syaru.ae2craftingoptimizer.access.CraftingIslandJobAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingJobTransactionAccess;
import com.syaru.ae2craftingoptimizer.api.execution.CraftingIslandStateUncertainException;
import com.syaru.ae2craftingoptimizer.engine.CheckedLongMath;
import java.util.Map;
import net.pedroksl.advanced_ae.common.logic.ElapsedTimeTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob", remap = false)
public abstract class AdvancedAeExecutingCraftingJobTransactionAccessMixin
        implements CraftingJobTransactionAccess, CraftingIslandJobAccess {
    @Shadow
    @Final
    private Map<IPatternDetails, Object> tasks;

    @Shadow
    @Final
    private ListCraftingInventory waitingFor;

    @Shadow
    @Final
    private ElapsedTimeTracker timeTracker;

    @Shadow
    @Final
    private CraftingLink link;

    @Shadow
    private GenericStack finalOutput;

    @Shadow
    private long remainingAmount;

    @Override
    public Map<IPatternDetails, Object> aco$getTasks() {
        return tasks;
    }

    @Override
    public ICraftingInventory aco$getWaitingFor() {
        return waitingFor;
    }

    @Override
    public Map<IPatternDetails, Object> aco$getIslandTasks() {
        return tasks;
    }

    @Override
    public GenericStack aco$getIslandFinalOutput() {
        return finalOutput;
    }

    @Override
    public long aco$getIslandRemainingAmount() {
        return remainingAmount;
    }

    @Override
    public boolean aco$canAcceptIslandOutput(AEKey key, long amount) {
        // 0以下の値やwaitingForをwrapさせる加算は、CPU会計へ一切入れない。
        if (key == null || amount <= 0L) {
            return false;
        }
        long currentWaiting = waitingFor.list.get(key);
        try {
            CheckedLongMath.add(
                    currentWaiting,
                    amount,
                    "AdvancedAE island waitingFor");
        } catch (ArithmeticException overflow) {
            return false;
        }

        boolean finalOutputKey =
                finalOutput != null && key.matches(finalOutput);
        if (!finalOutputKey) {
            return true;
        }
        // 既に配送済みの最終出力と今回分の合計が、Job残量を超える島を拒否する。
        if (remainingAmount < amount
                || currentWaiting > remainingAmount - amount) {
            return false;
        }
        return link.insert(key, amount, Actionable.SIMULATE) == amount;
    }

    @Override
    public void aco$stageIslandOutput(AEKey key, long amount) {
        long previousWaiting = waitingFor.list.get(key);
        // prepare後の変化も同じ検査へ通し、確認不能な部分stageを作らない。
        if (!aco$canAcceptIslandOutput(key, amount)) {
            throw new IllegalStateException(
                    "AdvancedAE island output can no longer be staged safely");
        }
        long nextWaiting = CheckedLongMath.add(
                previousWaiting,
                amount,
                "AdvancedAE island waitingFor");
        try {
            waitingFor.insert(key, amount, Actionable.MODULATE);
            // listener呼出しを含め、実カウンタが事前計算と一致した時だけ成功とする。
            if (waitingFor.list.get(key) != nextWaiting) {
                throw new IllegalStateException(
                        "AdvancedAE island waitingFor changed during output staging");
            }
        } catch (RuntimeException stagingFailure) {
            long currentWaiting = waitingFor.list.get(key);
            // 値が変わる前の例外なら追加のcallback rollbackは不要。
            boolean callbackRollbackComplete =
                    currentWaiting == previousWaiting;
            // 実際に全量stageされた場合だけ、公開APIで逆差分を通知して戻す。
            if (!callbackRollbackComplete
                    && currentWaiting == nextWaiting) {
                try {
                    long removed = waitingFor.extract(
                            key,
                            amount,
                            Actionable.MODULATE);
                    callbackRollbackComplete =
                            removed == amount
                                    && waitingFor.list.get(key) == previousWaiting;
                } catch (RuntimeException rollbackFailure) {
                    stagingFailure.addSuppressed(rollbackFailure);
                }
            }
            waitingFor.list.set(key, previousWaiting);
            waitingFor.list.removeZeros();
            if (!callbackRollbackComplete) {
                throw new CraftingIslandStateUncertainException(
                        "AdvancedAE island output staging callback could not be rolled back exactly",
                        stagingFailure);
            }
            throw stagingFailure;
        }
    }

    @Override
    public void aco$unstageIslandOutput(AEKey key, long amount) {
        long previousWaiting = waitingFor.list.get(key);
        // 登録済み量より大きい逆差分は、別処理が会計を変更した状態として拒否する。
        if (amount <= 0L || previousWaiting < amount) {
            throw new IllegalStateException(
                    "AdvancedAE island staged output cannot be rolled back exactly");
        }
        try {
            long removed = waitingFor.extract(
                    key,
                    amount,
                    Actionable.MODULATE);
            if (removed != amount) {
                throw new IllegalStateException(
                        "AdvancedAE island waitingFor changed during output rollback");
            }
            waitingFor.list.removeZeros();
        } catch (RuntimeException rollbackFailure) {
            // callback例外後も数値は元へ戻し、不確定状態を上位のJob停止処理へ渡す。
            waitingFor.list.set(key, previousWaiting);
            waitingFor.list.removeZeros();
            throw rollbackFailure;
        }
    }

    @Override
    public void aco$decrementIslandInternalOutput(AEKey key, long amount) {
        ((AdvancedAeElapsedTimeTrackerAccessor) (Object) timeTracker)
                .aco$invokeDecrementItems(amount, key.getType());
    }

    @Override
    public void aco$setIslandSuspended(boolean suspended) {
        // AdvancedAEにsuspended状態がないため、不確定Jobはcancel印を付けて次tickに安全終了させる。
        if (suspended) {
            link.cancel();
        }
    }
}

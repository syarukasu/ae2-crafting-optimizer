package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.access.CraftingJobTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingLogicTransactionAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingTaskProgressAccess;
import com.syaru.ae2craftingoptimizer.access.ExactCraftingJobAccess;
import com.syaru.ae2craftingoptimizer.api.batch.CountedPatternBatchTarget;
import com.syaru.ae2craftingoptimizer.api.batch.CountedProcessingTransfers;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchSourceReceiptStore;
import java.math.BigInteger;
import java.util.ArrayList;
import net.minecraft.world.level.Level;

/** Native AE2 accounting only; the original CPU grant is not enlarged. */
public final class CountedCraftingDispatch {
    public static final int NOT_HANDLED = -1;
    private CountedCraftingDispatch() {}

    public static int execute(Object logic, int grant, CraftingService service,
            IEnergyService energy, Level level) {
        if (!CountedProcessingTransfers.enabled() || grant <= 1
                || !(logic instanceof CraftingLogicTransactionAccess access)) return NOT_HANDLED;
        if (logic instanceof BatchSourceReceiptStore receipts
                && (!receipts.aco$isBatchSourceReceiptLedgerHealthy()
                    || receipts.aco$hasAnyUnresolvedBatchSourceReceipt())) return 0;
        var rawJob = access.aco$getExecutingJob();
        if (rawJob instanceof ExactCraftingJobAccess exact && exact.aco$isExactJob()) return NOT_HANDLED;
        if (!(rawJob instanceof CraftingJobTransactionAccess job)) return NOT_HANDLED;
        var iterator = job.aco$getTasks().entrySet().iterator();
        boolean inspected = false;
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!(entry.getValue() instanceof CraftingTaskProgressAccess progress)) return NOT_HANDLED;
            BigInteger remaining = progress.aco$getExactTaskProgress();
            if (remaining.signum() <= 0) continue;
            var providers = new ArrayList<ICraftingProvider>();
            for (var provider : service.getProviders(entry.getKey())) {
                // Preserve opaque callbacks and routing before any source ownership changes.
                if (!(provider instanceof CountedPatternBatchTarget)) return NOT_HANDLED;
                providers.add(provider);
            }
            if (providers.isEmpty()) continue;
            if (grant <= providers.size()) return NOT_HANDLED;
            inspected = true;
            int accepted = 0;
            for (int i = 0; i < providers.size() && accepted < grant && remaining.signum() > 0; i++) {
                int left = grant - accepted;
                int share = (int) (((long) left + providers.size() - i - 1) / (providers.size() - i));
                var result = CountedProcessingTransfers.transfer(entry.getKey(), remaining, share,
                        access.aco$getCraftingInventory(), job.aco$getWaitingFor(), providers.get(i), energy, level);
                if (result == null) return accepted == 0 ? NOT_HANDLED : accepted;
                if (result.executions() == 0) continue;
                // Acceptance has occurred. Fail closed, never retry or fabricate outputs.
                for (var output : result.expectedOutputs().entrySet()) {
                    long amount = output.getValue().longValueExact();
                    job.aco$getWaitingFor().insert(output.getKey(), amount, Actionable.MODULATE);
                }
                remaining = remaining.subtract(BigInteger.valueOf(result.executions()));
                progress.aco$setExactTaskProgress(remaining);
                accepted = Math.addExact(accepted, result.executions());
                access.aco$getCraftingOwner().aco$markCraftingOwnerDirty();
            }
            if (accepted > 0) {
                if (remaining.signum() == 0) iterator.remove();
                return accepted;
            }
            // #242: inspect the next task, not the same blocked task through native fallback.
        }
        return inspected ? 0 : NOT_HANDLED;
    }
}

package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.access.CraftingServiceCalculationHookAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingCalculationCacheAccess;
import com.syaru.ae2craftingoptimizer.access.CraftingProviderRefreshAccess;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationDeduplicator;
import com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationSnapshotContext;
import com.syaru.ae2craftingoptimizer.optimization.ServerPlanningThreadGuard;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingService.class, remap = false)
public abstract class CraftingServiceCalculationDeduplicationMixin
        implements CraftingServiceCalculationHookAccess, CraftingCalculationCacheAccess {
    @Shadow
    @Final
    private IGrid grid;

    @Unique
    private final CraftingCalculationDeduplicator.ServiceState aco$calculationCacheState =
            CraftingCalculationDeduplicator.createServiceState();

    @Override
    public CraftingCalculationDeduplicator.ServiceState aco$getCraftingCalculationCacheState() {
        return aco$calculationCacheState;
    }

    @Inject(
            method = "beginCraftingCalculation",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void aco$reuseActiveCraftingCalculation(
            Level level,
            ICraftingSimulationRequester requester,
            AEKey output,
            long amount,
            CalculationStrategy strategy,
            CallbackInfoReturnable<Future<ICraftingPlan>> cir) {
        // AE2本体の引数検証を先に成立させ、ACO側のNPEへ理由を変えない。
        if (level == null || requester == null || output == null) {
            return;
        }
        // off-thread互換呼出しではProvider flushやlive storage revision取得を行わない。
        if (!ServerPlanningThreadGuard.canCapture(level)) {
            return;
        }
        /*
         * Issue #167: Provider flushとdedupが同じHEAD注入順へ依存すると、旧世代Futureを
         * 返してから保留更新を確定し得る。保留状態を所有するMixinが存在する場合は、
         * 現在世代を読む前に同じCraftingService上で明示的にflushする。
         */
        if ((Object) this instanceof CraftingProviderRefreshAccess refreshAccess) {
            refreshAccess.aco$flushPendingProviderRefreshes();
        }
        // dedup無効時はActionSource取得やrequest key生成を行わず、AE2本体へ直ちに戻す。
        if (!ACOConfig.deduplicateActiveCraftingCalculations()) {
            return;
        }
        IActionSource actionSource = requester.getActionSource();
        Future<ICraftingPlan> activeCalculation = CraftingCalculationDeduplicator.findActive(
                (CraftingService) (Object) this,
                grid,
                level,
                requester,
                actionSource,
                output,
                amount,
                strategy);
        if (activeCalculation != null) {
            cir.setReturnValue(activeCalculation);
            return;
        }
        // 実際にjobを生成する呼出しだけ、constructorからsubmitへsnapshot revisionを運ぶ。
        CraftingCalculationSnapshotContext.begin(requester, actionSource);
    }

    @Redirect(
            method = "beginCraftingCalculation",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/ExecutorService;submit(Ljava/util/concurrent/Callable;)Ljava/util/concurrent/Future;"),
            require = 1)
    private Future<ICraftingPlan> aco$submitAndRememberActiveCraftingCalculation(
            ExecutorService executor,
            Callable<ICraftingPlan> calculation,
            Level level,
            ICraftingSimulationRequester requester,
            AEKey output,
            long amount,
            CalculationStrategy strategy) {
        CraftingCalculationSnapshotContext.CalculationRevision calculationRevision =
                CraftingCalculationSnapshotContext.finish();
        Future<ICraftingPlan> submitted = executor.submit(calculation);
        // dedup OFFまたはoff-thread互換呼出しでは、AE2が返したFutureをそのまま維持する。
        if (calculationRevision == null) {
            return submitted;
        }
        return CraftingCalculationDeduplicator.remember(
                (CraftingService) (Object) this,
                level,
                requester,
                output,
                amount,
                strategy,
                calculationRevision,
                submitted);
    }
}

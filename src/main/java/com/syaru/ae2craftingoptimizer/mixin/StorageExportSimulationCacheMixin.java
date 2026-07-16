package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.parts.automation.HandlerStrategy;
import appeng.parts.automation.StorageExportStrategy;
import com.syaru.ae2craftingoptimizer.optimization.BusTransferSimulationCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = StorageExportStrategy.class, remap = false)
public abstract class StorageExportSimulationCacheMixin {
    @Redirect(
            method = {"transfer", "push"},
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/parts/automation/HandlerStrategy;insert(Ljava/lang/Object;Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J"),
            require = 0)
    private long aco$cacheRejectedExternalInsert(
            HandlerStrategy<Object, Object> strategy,
            Object handler,
            AEKey what,
            long amount,
            Actionable mode) {
        if (mode == Actionable.SIMULATE
                && BusTransferSimulationCache.wasRejected(handler, what, amount)) {
            return 0;
        }

        long inserted = strategy.insert(handler, what, amount, mode);
        if (mode == Actionable.SIMULATE && inserted <= 0) {
            BusTransferSimulationCache.rememberRejected(handler, what, amount);
        }
        return inserted;
    }
}

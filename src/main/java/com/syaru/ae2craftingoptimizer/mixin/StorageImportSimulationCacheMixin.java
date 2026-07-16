package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.parts.automation.StorageImportStrategy;
import com.syaru.ae2craftingoptimizer.optimization.BusTransferSimulationCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = StorageImportStrategy.class, remap = false)
public abstract class StorageImportSimulationCacheMixin {
    @Redirect(
            method = "transfer",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/storage/MEStorage;insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J"),
            require = 0)
    private long aco$cacheRejectedNetworkInsert(
            MEStorage storage,
            AEKey what,
            long amount,
            Actionable mode,
            IActionSource source) {
        if (mode == Actionable.SIMULATE
                && BusTransferSimulationCache.wasRejected(storage, what, amount)) {
            return 0;
        }

        long inserted = storage.insert(what, amount, mode, source);
        if (mode == Actionable.SIMULATE && inserted <= 0) {
            BusTransferSimulationCache.rememberRejected(storage, what, amount);
        }
        return inserted;
    }
}

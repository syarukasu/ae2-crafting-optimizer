package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.menu.me.common.MEStorageMenu;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MEStorageMenu.class, remap = false)
public abstract class MEStorageMenuSyncOptimizationMixin {
    @Unique
    private int aco$menuTick;

    @Unique
    private int aco$availableStacksTick = Integer.MIN_VALUE;

    @Unique
    private KeyCounter aco$availableStacksSnapshot;

    @Unique
    private int aco$craftablesTick = Integer.MIN_VALUE;

    @Unique
    private Set<AEKey> aco$craftablesSnapshot;

    @Inject(method = "m_38946_", at = @At("HEAD"))
    private void aco$advanceMenuTick(CallbackInfo ci) {
        aco$menuTick++;
    }

    @Redirect(
            method = "m_38946_",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/storage/MEStorage;getAvailableStacks()Lappeng/api/stacks/KeyCounter;"),
            require = 0)
    private KeyCounter aco$getAvailableStacksSnapshot(MEStorage storage) {
        if (!ACOConfig.throttleTerminalInventorySnapshots()) {
            return storage.getAvailableStacks();
        }

        int interval = ACOConfig.getTerminalInventorySnapshotIntervalTicks();
        if (aco$availableStacksSnapshot != null && aco$menuTick - aco$availableStacksTick < interval) {
            return aco$copyCounter(aco$availableStacksSnapshot);
        }

        KeyCounter actual = storage.getAvailableStacks();
        aco$availableStacksSnapshot = aco$copyCounter(actual);
        aco$availableStacksTick = aco$menuTick;
        return actual;
    }

    @Inject(method = "getCraftablesFromGrid", at = @At("HEAD"), cancellable = true, require = 0)
    private void aco$getCachedCraftablesForMenu(CallbackInfoReturnable<Set<AEKey>> cir) {
        if (!ACOConfig.cacheTerminalCraftables() || aco$craftablesSnapshot == null) {
            return;
        }

        if (aco$menuTick - aco$craftablesTick < ACOConfig.getTerminalCraftableCacheTicks()) {
            cir.setReturnValue(aco$craftablesSnapshot);
        }
    }

    @Inject(method = "getCraftablesFromGrid", at = @At("RETURN"), require = 0)
    private void aco$rememberCraftablesForMenu(CallbackInfoReturnable<Set<AEKey>> cir) {
        if (!ACOConfig.cacheTerminalCraftables()) {
            return;
        }

        Set<AEKey> value = cir.getReturnValue();
        if (value != null) {
            aco$craftablesSnapshot = Set.copyOf(value);
            aco$craftablesTick = aco$menuTick;
        }
    }

    @Unique
    private static KeyCounter aco$copyCounter(KeyCounter source) {
        KeyCounter copy = new KeyCounter();
        if (source != null) {
            copy.addAll(source);
        }
        return copy;
    }
}

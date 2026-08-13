package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.NetworkCraftingSimulationState;
import com.syaru.ae2craftingoptimizer.access.ExactBigIntegerInventoryHookAccess;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.BigKeyCounterSidecars;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AE2がクラフト計算用に複製したKeyCounterへ、正確なBigInteger Snapshotも伝播する。 */
@Mixin(value = NetworkCraftingSimulationState.class, remap = false)
public abstract class NetworkCraftingSimulationStateBigIntegerSnapshotMixin
        implements ExactBigIntegerInventoryHookAccess {
    @Shadow
    @Final
    private KeyCounter list;

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void aco$copyExactNetworkInventory(
            IStorageService storageService,
            IActionSource actionSource,
            CallbackInfo ci) {
        // ActionSource欠落時はAE2コンストラクタも在庫を複製しないため、Sidecarも作らない。
        if (actionSource == null || !ACOConfig.enableExactBigIntegerInventorySnapshots()) {
            return;
        }
        BigKeyCounterSidecars.copyVisible(storageService.getCachedInventory(), list);
    }
}

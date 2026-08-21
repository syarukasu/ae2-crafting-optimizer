package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingService.class, remap = false)
public abstract class CraftingProviderRefreshCoalescingMixin {
    @Unique
    private final Set<IGridNode> aco$pendingProviderRefreshes =
            Collections.newSetFromMap(new IdentityHashMap<>());

    @Unique
    private boolean aco$flushingProviderRefreshes;

    @Inject(method = "refreshNodeCraftingProvider", at = @At("HEAD"), cancellable = true)
    private void aco$queueProviderRefresh(IGridNode node, CallbackInfo ci) {
        if (aco$flushingProviderRefreshes) {
            return;
        }

        if (!ACOConfig.coalesceCraftingProviderRefreshes()) {
            return;
        }

        ICraftingProvider provider = node.getService(ICraftingProvider.class);
        // issue #123: 外部Providerの遅延更新をACOが先に確定しないよう、通知を即時通過させる。
        if (!ProviderPatternGenerationTracker.isRefreshCoalescingSafe(provider)) {
            return;
        }

        aco$pendingProviderRefreshes.add(node);
        ci.cancel();
    }

    @Inject(method = "refreshNodeCraftingProvider", at = @At("TAIL"))
    private void aco$publishProviderRefresh(IGridNode node, CallbackInfo ci) {
        // Publish the new generation only after AE2 replaced its provider index.
        ProviderPatternGenerationTracker.shouldRefresh(node);
    }

    @Inject(method = "onServerEndTick", at = @At("HEAD"))
    private void aco$flushProviderRefreshesAtTickEnd(CallbackInfo ci) {
        aco$flushProviderRefreshes();
    }

    @Inject(method = "beginCraftingCalculation", at = @At("HEAD"))
    private void aco$flushProviderRefreshesBeforeCalculation(
            Level level,
            ICraftingSimulationRequester requester,
            AEKey what,
            long amount,
            CalculationStrategy strategy,
            CallbackInfoReturnable<?> cir) {
        aco$flushProviderRefreshes();
    }

    @Inject(method = "addNode", at = @At("HEAD"))
    private void aco$dropPendingRefreshOnNodeAdd(IGridNode node, CompoundTag savedData, CallbackInfo ci) {
        aco$pendingProviderRefreshes.remove(node);
    }

    @Inject(method = "addNode", at = @At("RETURN"))
    private void aco$publishProviderAfterNodeAdd(IGridNode node, CompoundTag savedData, CallbackInfo ci) {
        // addNode mutates AE2's provider index directly, so invalidate only after it completes.
        ProviderPatternGenerationTracker.forget(node);
        ProviderPatternGenerationTracker.remember(node);
    }

    @Inject(method = "removeNode", at = @At("HEAD"))
    private void aco$dropPendingRefreshOnNodeRemove(IGridNode node, CallbackInfo ci) {
        aco$pendingProviderRefreshes.remove(node);
    }

    @Inject(method = "removeNode", at = @At("RETURN"))
    private void aco$publishProviderAfterNodeRemove(IGridNode node, CallbackInfo ci) {
        // removeNode mutates AE2's provider index directly, so invalidate only after it completes.
        ProviderPatternGenerationTracker.forget(node);
    }

    @Unique
    private void aco$flushProviderRefreshes() {
        if (!ACOConfig.coalesceCraftingProviderRefreshes()
                || aco$flushingProviderRefreshes
                || aco$pendingProviderRefreshes.isEmpty()) {
            return;
        }

        var pending = new ArrayList<>(aco$pendingProviderRefreshes);
        aco$pendingProviderRefreshes.clear();
        aco$flushingProviderRefreshes = true;
        try {
            CraftingService service = (CraftingService) (Object) this;
            for (IGridNode node : pending) {
                // Collapse duplicates from one tick, but let the target method finish before
                // aco$publishProviderRefresh exposes the corresponding generation.
                service.refreshNodeCraftingProvider(node);
            }
        } finally {
            aco$flushingProviderRefreshes = false;
        }
    }
}

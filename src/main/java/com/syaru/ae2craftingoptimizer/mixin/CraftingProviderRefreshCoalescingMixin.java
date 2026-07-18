package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
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
            // refreshNodeCraftingProviderはクラフト索引だけでなく、Pattern Access Terminalへ
            // Providerのスロット配置変更を通知する。内容が同じでも通知自体は止めない。
            ProviderPatternGenerationTracker.shouldRefresh(node);
            return;
        }

        aco$pendingProviderRefreshes.add(node);
        ci.cancel();
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
        ProviderPatternGenerationTracker.forget(node);
    }

    @Inject(method = "addNode", at = @At("RETURN"))
    private void aco$rememberProviderAfterNodeAdd(IGridNode node, CompoundTag savedData, CallbackInfo ci) {
        ProviderPatternGenerationTracker.remember(node);
    }

    @Inject(method = "removeNode", at = @At("HEAD"))
    private void aco$dropPendingRefreshOnNodeRemove(IGridNode node, CallbackInfo ci) {
        aco$pendingProviderRefreshes.remove(node);
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
                // 同一tickの重複だけをまとめ、最終状態のAE2通知は必ず一回通す。
                // これを省略すると大容量Providerの端末スロットがクライアントとずれる。
                ProviderPatternGenerationTracker.shouldRefresh(node);
                service.refreshNodeCraftingProvider(node);
            }
        } finally {
            aco$flushingProviderRefreshes = false;
        }
    }
}

package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.access.CraftingProviderRefreshAccess;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import com.syaru.ae2craftingoptimizer.optimization.ServerPlanningThreadGuard;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingService.class, remap = false)
public abstract class CraftingProviderRefreshCoalescingMixin
        implements CraftingProviderRefreshAccess {
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
            // 通知をそのまま通し、世代はAE2索引更新が完了したTAILで確定する。
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

    @Inject(method = "onServerEndTick", at = @At("HEAD"))
    private void aco$flushProviderRefreshesAtTickEnd(CallbackInfo ci) {
        aco$flushPendingProviderRefreshes();
    }

    @Inject(method = "beginCraftingCalculation", at = @At("HEAD"))
    private void aco$flushProviderRefreshesBeforeCalculation(
            Level level,
            ICraftingSimulationRequester requester,
            AEKey what,
            long amount,
            CalculationStrategy strategy,
            CallbackInfoReturnable<?> cir) {
        // 互換MODがoff-threadで計算APIを呼んでも、AE2のProvider索引をそのthreadから変更しない。
        if (!ServerPlanningThreadGuard.canCapture(level)) {
            return;
        }
        aco$flushPendingProviderRefreshes();
    }

    @Inject(method = "refreshNodeCraftingProvider", at = @At("TAIL"), require = 1)
    private void aco$commitProviderGenerationAfterRefresh(IGridNode node, CallbackInfo ci) {
        /*
         * Issue #167: generationをAE2索引更新より先へ公開しない。内容が同じAE2 Providerでも
         * refresh通知自体は止めず、Trackerは世代を進める必要がある時だけ進める。
         */
        ProviderPatternGenerationTracker.shouldRefresh(node);
    }

    @Inject(method = "addNode", at = @At("HEAD"))
    private void aco$dropPendingRefreshOnNodeAdd(IGridNode node, CompoundTag savedData, CallbackInfo ci) {
        aco$pendingProviderRefreshes.remove(node);
    }

    @Inject(method = "addNode", at = @At("RETURN"))
    private void aco$rememberProviderAfterNodeAdd(IGridNode node, CompoundTag savedData, CallbackInfo ci) {
        // AE2がnodeを索引へ追加した後に旧snapshotを破棄し、新しい内容を正本として記録する。
        ProviderPatternGenerationTracker.forget(node);
        ProviderPatternGenerationTracker.remember(node);
    }

    @Inject(method = "removeNode", at = @At("HEAD"))
    private void aco$dropPendingRefreshOnNodeRemove(IGridNode node, CallbackInfo ci) {
        aco$pendingProviderRefreshes.remove(node);
    }

    @Inject(method = "removeNode", at = @At("RETURN"))
    private void aco$forgetProviderAfterNodeRemove(IGridNode node, CallbackInfo ci) {
        // AE2索引からnodeが消えた後に世代を進め、旧Graphを新世代として再利用させない。
        ProviderPatternGenerationTracker.forget(node);
    }

    @Unique
    @Override
    public void aco$flushPendingProviderRefreshes() {
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
                service.refreshNodeCraftingProvider(node);
            }
        } finally {
            aco$flushingProviderRefreshes = false;
        }
    }
}

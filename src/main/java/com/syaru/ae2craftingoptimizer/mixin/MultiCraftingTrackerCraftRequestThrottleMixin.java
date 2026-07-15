package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.helpers.MultiCraftingTracker;
import com.syaru.ae2craftingoptimizer.optimization.CraftingRequestThrottle;
import java.util.concurrent.Future;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MultiCraftingTracker.class, remap = false)
public abstract class MultiCraftingTrackerCraftRequestThrottleMixin {
    @Shadow
    @Final
    private ICraftingRequester owner;

    @Shadow
    private Future<ICraftingPlan>[] jobs;

    @Shadow
    private ICraftingLink[] links;

    @Unique
    private boolean aco$hadJobAtStart;

    @Unique
    private boolean aco$hadLinkAtStart;

    @Unique
    private int aco$slot;

    @Unique
    private AEKey aco$key;

    @Unique
    private long aco$amount;

    @Inject(method = "handleCrafting", at = @At("HEAD"), cancellable = true)
    private void aco$throttleRepeatedFailedCraftingRequest(
            int slot,
            AEKey key,
            long amount,
            Level level,
            ICraftingService craftingService,
            IActionSource source,
            CallbackInfoReturnable<Boolean> cir) {
        aco$slot = slot;
        aco$key = key;
        aco$amount = amount;
        aco$hadJobAtStart = aco$getJob(slot) != null;
        aco$hadLinkAtStart = aco$getLink(slot) != null;

        if (!aco$hadJobAtStart
                && !aco$hadLinkAtStart
                && CraftingRequestThrottle.shouldThrottle(owner, slot, key, amount)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "handleCrafting", at = @At("RETURN"))
    private void aco$rememberFailedCraftingRequest(
            int slot,
            AEKey key,
            long amount,
            Level level,
            ICraftingService craftingService,
            IActionSource source,
            CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            return;
        }

        if (aco$slot == slot
                && aco$key == key
                && aco$amount == amount
                && aco$hadJobAtStart
                && !aco$hadLinkAtStart
                && aco$getJob(slot) == null
                && aco$getLink(slot) == null) {
            CraftingRequestThrottle.recordFailure(owner, slot, key, amount);
        }
    }

    @Unique
    private Future<ICraftingPlan> aco$getJob(int slot) {
        return jobs != null && slot >= 0 && slot < jobs.length ? jobs[slot] : null;
    }

    @Unique
    private ICraftingLink aco$getLink(int slot) {
        return links != null && slot >= 0 && slot < links.length ? links[slot] : null;
    }
}

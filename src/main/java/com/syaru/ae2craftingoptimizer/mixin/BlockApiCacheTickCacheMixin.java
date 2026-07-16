package com.syaru.ae2craftingoptimizer.mixin;

import appeng.util.BlockApiCache;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.ServerTickClock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockApiCache.class, remap = false)
public abstract class BlockApiCacheTickCacheMixin<C> {
    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private BlockPos fromPos;

    @Shadow
    @Final
    private Capability<C> capability;

    @Unique
    private long[] aco$cachedTicks;

    @Unique
    private Object[] aco$cachedBlockEntities;

    @Unique
    private Object[] aco$cachedCapabilities;

    @Unique
    private Object[] aco$cachedOptionals;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aco$initializeTickCache(
            Capability<C> capability,
            ServerLevel level,
            BlockPos fromPos,
            CallbackInfo ignored) {
        aco$cachedTicks = new long[Direction.values().length];
        java.util.Arrays.fill(aco$cachedTicks, Long.MIN_VALUE);
        aco$cachedBlockEntities = new Object[Direction.values().length];
        aco$cachedCapabilities = new Object[Direction.values().length];
        aco$cachedOptionals = new Object[Direction.values().length];
    }

    @Inject(method = "find", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void aco$reuseCapabilityForCurrentTick(Direction fromSide, CallbackInfoReturnable<C> cir) {
        if (!ACOConfig.cacheAdjacentCapabilityLookups() || aco$cachedTicks == null) {
            return;
        }

        long tick = ServerTickClock.currentTick();
        int index = fromSide.ordinal();
        boolean tickHit = tick > 0 && aco$cachedTicks[index] == tick;
        boolean longLivedHit = ACOConfig.cacheAdjacentCapabilitiesAcrossTicks()
                && aco$cachedOptionals[index] instanceof LazyOptional<?> optional
                && optional.isPresent();
        if ((!tickHit && !longLivedHit) || aco$cachedCapabilities[index] == null) {
            return;
        }

        BlockEntity currentBlockEntity = level.getBlockEntity(fromPos);
        if (aco$cachedBlockEntities[index] == currentBlockEntity) {
            cir.setReturnValue((C) aco$cachedCapabilities[index]);
        } else {
            aco$clear(index);
        }
    }

    @Inject(method = "find", at = @At("RETURN"))
    private void aco$rememberCapabilityForCurrentTick(Direction fromSide, CallbackInfoReturnable<C> cir) {
        if (!ACOConfig.cacheAdjacentCapabilityLookups() || aco$cachedTicks == null) {
            return;
        }

        C capability = cir.getReturnValue();
        if (capability == null) {
            return;
        }

        int index = fromSide.ordinal();
        aco$cachedTicks[index] = ServerTickClock.currentTick();
        aco$cachedBlockEntities[index] = level.getBlockEntity(fromPos);
        aco$cachedCapabilities[index] = capability;

        if (ACOConfig.cacheAdjacentCapabilitiesAcrossTicks() && aco$cachedBlockEntities[index] instanceof BlockEntity be) {
            LazyOptional<C> optional = be.getCapability(this.capability, fromSide);
            if (optional.isPresent()) {
                aco$cachedOptionals[index] = optional;
                optional.addListener(invalidated -> {
                    if (aco$cachedOptionals[index] == invalidated) {
                        aco$clear(index);
                    }
                });
            }
        }
    }

    @Unique
    private void aco$clear(int index) {
        aco$cachedTicks[index] = Long.MIN_VALUE;
        aco$cachedBlockEntities[index] = null;
        aco$cachedCapabilities[index] = null;
        aco$cachedOptionals[index] = null;
    }
}

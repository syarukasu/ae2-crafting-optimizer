package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEKey;
import appeng.api.util.IConfigManager;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;
import com.syaru.ae2craftingoptimizer.api.batch.CountedPatternBatchTarget;
import com.syaru.ae2craftingoptimizer.optimization.CountedItemCapacity;
import com.syaru.ae2craftingoptimizer.optimization.CountedNativeContracts;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderLogicCountedTargetMixin implements CountedPatternBatchTarget {
    @Shadow @Final private PatternProviderLogicHost host;
    @Shadow @Final private IManagedGridNode mainNode;
    @Shadow @Final private List<IPatternDetails> patterns;
    @Shadow public abstract IConfigManager getConfigManager();
    @Shadow public abstract LockCraftingMode getCraftingLockedReason();
    @Shadow public abstract boolean isBlocking();
    @Shadow public abstract boolean isBusy();
    @Shadow private Set<Direction> getActiveSides() { throw new AssertionError(); }
    @Shadow private PatternProviderTarget findAdapter(Direction side) { throw new AssertionError(); }

    @Override
    public long aco$countedExecutionLimit(IPatternDetails pattern, Map<AEKey, BigInteger> inputs, long offered) {
        if (!CountedNativeContracts.nativeProvider(this)
                || !CountedNativeContracts.defaultExternalPush(pattern)) return -1;
        if (offered <= 0 || isBusy() || !mainNode.isActive() || !patterns.contains(pattern)
                || getCraftingLockedReason() != LockCraftingMode.NONE) return 0;
        // #242: never batch across a boundary that native dispatch checks after each recipe.
        if (isBlocking() || getConfigManager().getSetting(Settings.LOCK_CRAFTING_MODE) != LockCraftingMode.NONE)
            return Math.min(1, offered);
        var be = host.getBlockEntity();
        var level = be.getLevel();
        var sides = getActiveSides();
        if (sides.size() > 1) return Math.min(1, offered);
        long safe = offered;
        boolean found = false;
        for (var direction : sides) {
            var pos = be.getBlockPos().relative(direction);
            var adjacent = level.getBlockEntity(pos);
            var side = direction.getOpposite();
            var machine = ICraftingMachine.of(level, pos, side, adjacent);
            if (machine != null && machine.acceptsPlans()) return -1;
            var adapter = findAdapter(direction);
            if (adapter == null) continue;
            long room;
            if (inputs.size() == 1) {
                var entry = inputs.entrySet().iterator().next();
                long requested = entry.getValue().multiply(BigInteger.valueOf(offered))
                        .min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
                long accepted = adapter.insert(entry.getKey(), requested, Actionable.SIMULATE);
                if (accepted < 0) return -1;
                room = BigInteger.valueOf(accepted).divide(entry.getValue())
                        .min(BigInteger.valueOf(offered)).longValueExact();
            } else {
                if (adjacent == null) return -1;
                if (adjacent.getCapability(appeng.capabilities.Capabilities.STORAGE, side).isPresent()) return -1;
                var handler = adjacent.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
                room = CountedItemCapacity.limit(handler, inputs, offered);
                if (room < 0) return -1;
            }
            // Any face selected by native round-robin must fit the whole payload.
            if (room == 0) return 0;
            safe = Math.min(safe, room);
            found = true;
        }
        return found ? safe : 0;
    }
}

package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.util.IConfigManager;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.syaru.ae2craftingoptimizer.access.PatternProviderTargetAccess;
import java.util.Collection;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** AE2 Pattern Providerの読み取り専用ターゲット境界。 */
@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderLogicTargetAccessMixin
        implements PatternProviderTargetAccess {
    @Shadow
    @Final
    private PatternProviderLogicHost host;

    @Shadow
    public abstract IConfigManager getConfigManager();

    @Shadow
    public abstract boolean isBlocking();

    @Override
    public BlockEntity aco$getProviderBlockEntity() {
        return host.getBlockEntity();
    }

    @Override
    public Collection<Direction> aco$getProviderTargets() {
        return host.getTargets();
    }

    @Override
    public IConfigManager aco$getProviderConfigManager() {
        return getConfigManager();
    }

    @Override
    public boolean aco$isProviderBlocking() {
        return isBlocking();
    }
}

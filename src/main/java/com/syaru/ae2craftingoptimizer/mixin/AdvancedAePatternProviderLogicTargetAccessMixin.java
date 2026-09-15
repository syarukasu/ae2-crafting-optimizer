package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.util.IConfigManager;
import com.syaru.ae2craftingoptimizer.access.PatternProviderTargetAccess;
import java.util.Collection;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** Advanced AE Pattern Providerの読み取り専用ターゲット境界。 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic", remap = false)
public abstract class AdvancedAePatternProviderLogicTargetAccessMixin
        implements PatternProviderTargetAccess {
    @Shadow
    @Final
    private AdvPatternProviderLogicHost host;

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

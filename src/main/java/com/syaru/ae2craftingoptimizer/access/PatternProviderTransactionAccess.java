package com.syaru.ae2craftingoptimizer.access;

import appeng.api.util.IConfigManager;
import java.util.Collection;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Typed access to the provider state required by transactional batching. */
public interface PatternProviderTransactionAccess {
    BlockEntity aco$getProviderBlockEntity();

    Collection<Direction> aco$getProviderTargets();

    IConfigManager aco$getProviderConfigManager();

    boolean aco$isProviderBlocking();
}

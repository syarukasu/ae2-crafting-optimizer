package com.syaru.ae2craftingoptimizer.access;

import appeng.api.util.IConfigManager;
import appeng.api.crafting.IPatternDetails;
import com.syaru.ae2craftingoptimizer.api.batch.v2.BatchOwnershipProof;
import com.syaru.ae2craftingoptimizer.api.batch.v2.PreparedPatternBatch;
import java.util.Collection;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Typed access to the provider state required by transactional batching. */
public interface PatternProviderTransactionAccess {
    BlockEntity aco$getProviderBlockEntity();

    Collection<Direction> aco$getProviderTargets();

    IConfigManager aco$getProviderConfigManager();

    boolean aco$isProviderBlocking();

    /**
     * Aggregate入力をProvider自身の永続send bufferへ移し、その同じNBT ownerへReceiptを記録する。
     * nullは一切の所有権移動が起きなかったことを示す。
     */
    BatchOwnershipProof aco$stageOwnedBatch(
            IPatternDetails pattern,
            Direction providerSide,
            PreparedPatternBatch prepared,
            String patternFingerprint,
            long gameTick);
}

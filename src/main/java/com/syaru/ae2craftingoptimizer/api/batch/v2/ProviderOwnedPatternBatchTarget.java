package com.syaru.ae2craftingoptimizer.api.batch.v2;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 外部Inventoryではなく、Pattern Provider自身がBatchの永続所有者になることを示す。
 *
 * <p>AACのような実クラフト設備が実装する。ACOは返されたBlockEntityの位置を
 * 永続Transaction Journalの復旧先として記録する。</p>
 */
public interface ProviderOwnedPatternBatchTarget {
    BlockEntity aco$getProviderOwnedBatchTarget();
}

package com.syaru.ae2craftingoptimizer.access;

import appeng.api.util.IConfigManager;
import java.util.Collection;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Pattern Providerから静的な配置情報だけを読むMixin用契約。
 *
 * <p>ACOはこの境界からsend buffer、Receipt、進捗を書き換えない。
 * ターゲットが実バッチを所有する場合は、外部アダプターが
 * {@code PatternBatchV2Api}で明示的に所有権を引き受ける。</p>
 */
public interface PatternProviderTargetAccess {
    BlockEntity aco$getProviderBlockEntity();

    Collection<Direction> aco$getProviderTargets();

    IConfigManager aco$getProviderConfigManager();

    boolean aco$isProviderBlocking();
}

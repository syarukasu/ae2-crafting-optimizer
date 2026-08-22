package com.syaru.ae2craftingoptimizer.engine.craftingtable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.access.PatternProviderTransactionAccess;
import com.syaru.ae2craftingoptimizer.api.batch.v2.ProviderOwnedPatternBatchTarget;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchTarget;
import com.syaru.ae2craftingoptimizer.scheduler.PatternProviderRoutingCache;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * ACOの汎用CraftingTableBatchTargetをAE2 Pattern Providerから解決する正本。
 *
 * <p>特定アドオンのBlockEntityやmod IDを参照せず、Providerが公開する所有Targetまたは
 * Provider接続方向だけを使う。これにより外部実装も同じAPIで利用できる。</p>
 */
public final class CraftingTableBatchTargetResolver {
    private CraftingTableBatchTargetResolver() {
    }

    public static Resolution resolve(
            CraftingService service,
            IPatternDetails pattern,
            Level level) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(level, "level");
        Map<Long, BlockEntity> targets = new LinkedHashMap<>();
        boolean sawUnloadedTarget = false;
        // AE2の世代付き候補だけを巡回し、同じProviderを毎tick全探索しない。
        for (ICraftingProvider provider : PatternProviderRoutingCache.candidates(
                service,
                pattern)) {
            // Provider自身が永続Targetを公開していれば、その契約を最優先で使う。
            if (provider instanceof ProviderOwnedPatternBatchTarget owned) {
                BlockEntity target = owned.aco$getProviderOwnedBatchTarget();
                sawUnloadedTarget |= addLoadedOrKnownTarget(
                        targets,
                        target,
                        level);
            }
            // 接続方向から解決できないProviderは、別の実装経路を持たないため除外する。
            if (!(provider instanceof PatternProviderTransactionAccess access)) {
                continue;
            }
            BlockEntity providerEntity = access.aco$getProviderBlockEntity();
            // ProviderのBlockEntityが別ワールドまたは未生成なら、隣接Targetを推測しない。
            if (providerEntity == null
                    || providerEntity.getLevel() != level) {
                continue;
            }
            // Providerが公開した接続方向の隣接BlockEntityだけをTarget候補にする。
            for (Direction direction : access.aco$getProviderTargets()) {
                BlockPos targetPosition = providerEntity.getBlockPos().relative(direction);
                // 未ロードチャンクは強制ロードせず、次tickの再試行理由として残す。
                if (!level.isLoaded(targetPosition)) {
                    sawUnloadedTarget = true;
                    continue;
                }
                BlockEntity target = level.getBlockEntity(targetPosition);
                // ACOの汎用Target契約を実装しない隣接機械は、物理Batch先にしない。
                if (target instanceof CraftingTableBatchTarget) {
                    targets.putIfAbsent(targetPosition.asLong(), target);
                }
            }
        }
        return new Resolution(
                List.copyOf(new ArrayList<>(targets.values())),
                sawUnloadedTarget);
    }

    private static boolean addLoadedOrKnownTarget(
            Map<Long, BlockEntity> targets,
            BlockEntity target,
            Level level) {
        if (!(target instanceof CraftingTableBatchTarget)) {
            return false;
        }
        BlockPos position = target.getBlockPos();
        // 別LevelのTargetは現在Jobへ使えないため、未ロード扱いで永久再試行しない。
        if (target.getLevel() != level) {
            return false;
        }
        // 同じLevelの未ロードTargetだけは、次tickで復帰する一時待機として返す。
        if (!level.isLoaded(position)) {
            return true;
        }
        targets.putIfAbsent(position.asLong(), target);
        return false;
    }

    public record Resolution(
            List<BlockEntity> targets,
            boolean sawUnloadedTarget) {
        public Resolution {
            targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        }

        public boolean ready() {
            return !targets.isEmpty();
        }

        public String waitReason() {
            if (ready()) {
                return "";
            }
            if (sawUnloadedTarget) {
                return "waiting for a loaded crafting-table batch target";
            }
            return "no generic CraftingTableBatchTarget is registered for the pattern";
        }
    }
}

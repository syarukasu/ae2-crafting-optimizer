package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.access.CraftingTaskProgressAccess;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;

/**
 * 入力待ち中の同一Jobについて、検証済みCrafting Islandを再利用する弱参照キャッシュ。
 */
final class CraftingIslandCompilationCache {
    private static final Map<Object, Entry> ENTRIES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private CraftingIslandCompilationCache() {
    }

    static void clear() {
        ENTRIES.clear();
    }

    static Optional<List<CompiledCraftingIsland<AEKey, IPatternDetails>>> getOrCompile(
            Object jobIdentity,
            Map<IPatternDetails, Object> liveTasks,
            Level level,
            int maximumPatterns,
            int maximumBits,
            long recipeGeneration) {
        Entry cached = ENTRIES.get(jobIdentity);
        // 設定、レシピ世代、島内Task残数が同一なら隣接表と純差分を再利用する。
        if (cached != null
                && cached.maximumPatterns == maximumPatterns
                && cached.maximumBits == maximumBits
                && cached.recipeGeneration == recipeGeneration
                && cached.matches(liveTasks)) {
            OptimizationMetrics.recordCraftingIslandCache(true);
            return Optional.of(cached.islands);
        }

        OptimizationMetrics.recordCraftingIslandCache(false);
        Optional<List<CompiledCraftingIsland<AEKey, IPatternDetails>>> compiled =
                Ae2CraftingIslandCompiler.tryCompile(
                        liveTasks,
                        level,
                        maximumPatterns,
                        maximumBits);
        // 証明済み島が一件以上ある結果だけを保持し、失敗や単一Pattern Jobは再評価可能にする。
        if (compiled.isPresent() && !compiled.orElseThrow().isEmpty()) {
            Entry replacement = new Entry(
                    maximumPatterns,
                    maximumBits,
                    recipeGeneration,
                    compiled.orElseThrow());
            ENTRIES.put(jobIdentity, replacement);
        } else {
            ENTRIES.remove(jobIdentity);
        }
        return compiled;
    }

    private record Entry(
            int maximumPatterns,
            int maximumBits,
            long recipeGeneration,
            List<CompiledCraftingIsland<AEKey, IPatternDetails>> islands) {
        private Entry {
            islands = List.copyOf(islands);
        }

        private boolean matches(Map<IPatternDetails, Object> liveTasks) {
            // キャッシュした全島のTask参照と残数を確認し、通常配送後の古い純差分を使わない。
            for (CompiledCraftingIsland<AEKey, IPatternDetails> island : islands) {
                for (CompiledCraftingIsland.Task<AEKey, IPatternDetails> task :
                        island.tasks()) {
                    Object rawProgress = liveTasks.get(task.pattern());
                    // Taskの削除、Accessor欠落、残数変更はいずれも再コンパイル条件になる。
                    if (!(rawProgress instanceof CraftingTaskProgressAccess progress)
                            || progress.aco$getTaskProgress()
                                    != task.executions().longValueExact()) {
                        return false;
                    }
                }
            }
            return true;
        }
    }
}

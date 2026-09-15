package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.integration.AppliedECompatibility;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** AE2のProvider索引更新後に内容世代を確定し、Compiled Graphの再利用境界を管理する。 */
public final class ProviderPatternGenerationTracker {
    /** ACOが同一tick通知を安全に畳み込める、AE2本体所有クラスのパッケージ接頭辞。 */
    private static final String AE2_IMPLEMENTATION_PREFIX = "appeng.";
    private static final Map<IGridNode, Snapshot> SNAPSHOTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicLong GENERATION = new AtomicLong(1L);

    private ProviderPatternGenerationTracker() {
    }

    public static boolean shouldRefresh(IGridNode node) {
        // 重複抑制をOFFにしてもCompiled Graphの失効世代は必ず進める。
        if (!ACOConfig.trackProviderPatternGenerations()) {
            advanceGeneration();
            return true;
        }
        ICraftingProvider provider = node.getService(ICraftingProvider.class);
        // issue #123: 外部Providerは内部更新時機をACOが証明できないため、通知を常に保存する。
        if (!isRefreshCoalescingSafe(provider) || AppliedECompatibility.isDynamicProvider(provider)) {
            // AppliedE本家はこの比較で全既知アイテムのEMCを再取得する。
            // 同一tick通知は呼出側のSetでまとめ、最終通知は無条件でAE2へ渡す。
            synchronized (SNAPSHOTS) {
                SNAPSHOTS.put(node, Snapshot.DYNAMIC);
            }
            OptimizationMetrics.recordAppliedEDynamicProviderRefresh();
            advanceGeneration();
            return true;
        }
        Snapshot current = snapshot(provider);
        synchronized (SNAPSHOTS) {
            Snapshot previous = SNAPSHOTS.put(node, current);
            if (current.equals(previous)) {
                return false;
            }
        }
        advanceGeneration();
        return true;
    }

    public static void remember(IGridNode node) {
        if (!ACOConfig.trackProviderPatternGenerations()) {
            return;
        }
        ICraftingProvider provider = node.getService(ICraftingProvider.class);
        synchronized (SNAPSHOTS) {
            SNAPSHOTS.put(
                    node,
                    !isRefreshCoalescingSafe(provider) || AppliedECompatibility.isDynamicProvider(provider)
                            ? Snapshot.DYNAMIC
                            : snapshot(provider));
        }
    }

    /** AE2本体が所有するProviderだけを、内容Snapshotによる通知間引き対象にする。 */
    public static boolean isRefreshCoalescingSafe(ICraftingProvider provider) {
        if (provider == null) {
            return false;
        }
        return isRefreshCoalescingSafe(provider.getClass().getName());
    }

    static boolean isRefreshCoalescingSafe(String implementationName) {
        return implementationName != null && implementationName.startsWith(AE2_IMPLEMENTATION_PREFIX);
    }

    public static void forget(IGridNode node) {
        synchronized (SNAPSHOTS) {
            SNAPSHOTS.remove(node);
        }
        advanceGeneration();
    }

    public static long generation() {
        return GENERATION.get();
    }

    public static void clear() {
        synchronized (SNAPSHOTS) {
            SNAPSHOTS.clear();
        }
        advanceGeneration();
    }

    private static void advanceGeneration() {
        GENERATION.updateAndGet(value -> {
            // Issue #167: 1へwrapすると古いCompiled GraphとのABA一致を作るため明示失敗する。
            if (value == Long.MAX_VALUE) {
                throw new IllegalStateException("provider pattern generation exhausted");
            }
            return value + 1L;
        });
    }

    private static Snapshot snapshot(ICraftingProvider provider) {
        if (provider == null) {
            return Snapshot.EMPTY;
        }
        List<PatternSnapshot> patterns = new ArrayList<>();
        for (IPatternDetails pattern : provider.getAvailablePatterns()) {
            patterns.add(PatternSnapshot.of(pattern));
        }
        Set<AEKey> emitables = new HashSet<>(provider.getEmitableItems());
        return new Snapshot(provider.getPatternPriority(), List.copyOf(patterns), Set.copyOf(emitables));
    }

    private record Snapshot(int priority, List<PatternSnapshot> patterns, Set<AEKey> emitables) {
        private static final Snapshot EMPTY = new Snapshot(0, List.of(), Set.of());
        private static final Snapshot DYNAMIC = new Snapshot(Integer.MIN_VALUE, List.of(), Set.of());
    }

    private record PatternSnapshot(
            String implementation,
            AEKey definition,
            List<GenericStack> outputs,
            List<InputSnapshot> inputs,
            boolean externalPush) {
        private static PatternSnapshot of(IPatternDetails pattern) {
            List<InputSnapshot> inputs = new ArrayList<>();
            for (IPatternDetails.IInput input : pattern.getInputs()) {
                inputs.add(new InputSnapshot(
                        input.getMultiplier(),
                        List.of(input.getPossibleInputs().clone())));
            }
            return new PatternSnapshot(
                    pattern.getClass().getName(),
                    pattern.getDefinition(),
                    List.of(pattern.getOutputs().clone()),
                    List.copyOf(inputs),
                    pattern.supportsPushInputsToExternalInventory());
        }
    }

    private record InputSnapshot(long multiplier, List<GenericStack> possibleInputs) {
    }
}

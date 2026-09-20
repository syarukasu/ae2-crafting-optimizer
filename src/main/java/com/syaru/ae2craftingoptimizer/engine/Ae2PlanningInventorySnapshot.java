package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** AE2がserver threadで取得したlong在庫値を、workerへ渡せる不変値へ固定する。 */
public final class Ae2PlanningInventorySnapshot {
    private final Map<AEKey, Long> amounts;
    private final Map<Object, java.util.List<AEKey>> fuzzyGroups;

    private Ae2PlanningInventorySnapshot(Map<AEKey, Long> amounts) {
        this(amounts, Map.of());
    }

    private Ae2PlanningInventorySnapshot(Map<AEKey, Long> amounts,
            Map<Object, java.util.List<AEKey>> fuzzyGroups) {
        this.amounts = Map.copyOf(amounts);
        this.fuzzyGroups = java.util.Collections.unmodifiableMap(new java.util.IdentityHashMap<>(fuzzyGroups));
    }

    public static Ae2PlanningInventorySnapshot capture(KeyCounter source) {
        Objects.requireNonNull(source, "source");
        Map<AEKey, Long> amounts = new LinkedHashMap<>();
        // AE2のNetworkCraftingSimulationStateが公開した正数だけを不変Mapへ固定する。
        for (var entry : source) {
            long amount = entry.getLongValue();
            if (amount > 0L) {
                amounts.put(entry.getKey(), amount);
            }
        }
        return new Ae2PlanningInventorySnapshot(amounts, captureFuzzyGroups(source, amounts.keySet()));
    }

    /** rootから到達するキーだけを固定し、巨大ME在庫全体の二重複製を避ける。 */
    static Ae2PlanningInventorySnapshot captureReferenced(
            KeyCounter source,
            Iterable<AEKey> referencedKeys,
            AEKey requestedOutput) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(referencedKeys, "referencedKeys");
        Objects.requireNonNull(requestedOutput, "requestedOutput");
        Map<AEKey, Long> amounts = new LinkedHashMap<>();
        Map<Object, java.util.List<AEKey>> fuzzy = new java.util.IdentityHashMap<>();
        // グラフのノード順で一度ずつ引き、注文出力自身はAE2と同じく在庫から除外する。
        for (AEKey key : referencedKeys) {
            if (!fuzzy.containsKey(key.getPrimaryKey())) {
                var matches = source.findFuzzy(key, appeng.api.config.FuzzyMode.IGNORE_ALL);
                var ordered = new java.util.ArrayList<AEKey>();
                for (var match : matches) {
                    ordered.add(match.getKey());
                    if (match.getLongValue() > 0 && !requestedOutput.equals(match.getKey())) {
                        amounts.put(match.getKey(), match.getLongValue());
                    }
                }
                fuzzy.put(key.getPrimaryKey(), java.util.List.copyOf(ordered));
            }
            if (requestedOutput.equals(key)) {
                continue;
            }
            long amount = source.get(key);
            if (amount > 0L) {
                amounts.put(key, amount);
            }
        }
        return new Ae2PlanningInventorySnapshot(amounts, fuzzy);
    }

    private static Map<Object, java.util.List<AEKey>> captureFuzzyGroups(KeyCounter source, Iterable<AEKey> keys) {
        Map<Object, java.util.List<AEKey>> groups = new java.util.IdentityHashMap<>();
        for (AEKey key : keys) groups.computeIfAbsent(key.getPrimaryKey(), ignored -> source
                .findFuzzy(key, appeng.api.config.FuzzyMode.IGNORE_ALL).stream().map(Map.Entry::getKey).toList());
        return groups;
    }

    java.util.List<AEKey> fuzzyKeys(AEKey key) {
        return fuzzyGroups.getOrDefault(key.getPrimaryKey(), java.util.List.of());
    }

    long amount(AEKey key) {
        return amounts.getOrDefault(key, 0L);
    }
}

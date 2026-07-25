package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CraftConfirm画面へ送る、容量と素材別数量のBigInteger正本。
 *
 * <p>AE2本来のSummaryは全フィールドがlongなので、Serverでは安全に飽和させた
 * 互換Summaryを作り、Clientにはlongを越える行だけをACO packetで追加同期する。</p>
 */
public final class BigCraftingPlanSummary {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final BigInteger usedBytes;
    private final boolean simulation;
    private final Map<AEKey, Entry> entries;

    public BigCraftingPlanSummary(
            BigInteger usedBytes,
            boolean simulation,
            Map<AEKey, Entry> entries) {
        int maximumBits = ACOConfig.getBigIntegerMaximumBits();
        this.usedBytes = BigCountMath.requireMaximumBits(
                Objects.requireNonNull(usedBytes, "usedBytes"),
                "craft-confirm/usedBytes",
                maximumBits);
        this.simulation = simulation;

        Map<AEKey, Entry> copy = new LinkedHashMap<>();
        Objects.requireNonNull(entries, "entries").forEach((key, entry) -> {
            Objects.requireNonNull(key, "entry key");
            Objects.requireNonNull(entry, "entry");
            entry.validate("craft-confirm/" + key.getId(), maximumBits);
            copy.put(key, entry);
        });
        this.entries = Collections.unmodifiableMap(copy);
    }

    public static BigCraftingPlanSummary from(WideCraftingPlan plan) {
        Objects.requireNonNull(plan, "plan");
        int maximumBits = ACOConfig.getBigIntegerMaximumBits();
        Map<AEKey, MutableEntry> stats = new LinkedHashMap<>();
        BigInteger exactBytes;

        if (plan instanceof BigIntegerCraftingPlan bigPlan) {
            exactBytes = bigPlan.exactBytes();
            mergeCounts(
                    stats,
                    bigPlan.exactPlan().usedInventory(),
                    CounterTarget.STORED,
                    maximumBits);
            mergeCounts(
                    stats,
                    bigPlan.exactPlan().missing(),
                    CounterTarget.MISSING,
                    maximumBits);
            mergeCounts(
                    stats,
                    bigPlan.exactPlan().emitted(),
                    CounterTarget.EMITTED,
                    maximumBits);
            mergePatternOutputs(
                    stats,
                    bigPlan.exactPatternTimes(),
                    maximumBits);
        } else if (plan instanceof BigCapacityCraftingPlan bigCapacityPlan) {
            exactBytes = bigCapacityPlan.exactBytes();
            mergeLongCounter(stats, plan.usedItems(), CounterTarget.STORED, maximumBits);
            mergeLongCounter(stats, plan.missingItems(), CounterTarget.MISSING, maximumBits);
            mergeLongCounter(stats, plan.emittedItems(), CounterTarget.EMITTED, maximumBits);
            mergeLongPatternOutputs(stats, plan.patternTimes(), maximumBits);
        } else {
            throw new IllegalArgumentException(
                    "Big crafting confirmation requires an ACO wide plan");
        }

        Map<AEKey, Entry> immutableEntries = new LinkedHashMap<>();
        stats.forEach((key, value) -> immutableEntries.put(key, value.freeze()));
        return new BigCraftingPlanSummary(
                exactBytes,
                plan.simulation(),
                immutableEntries);
    }

    public BigInteger usedBytes() {
        return usedBytes;
    }

    public boolean simulation() {
        return simulation;
    }

    public Map<AEKey, Entry> entries() {
        return entries;
    }

    /** Clientでlong表示を置換する必要がある行だけを抽出する。 */
    public Map<AEKey, Entry> exactDisplayEntries() {
        Map<AEKey, Entry> result = new LinkedHashMap<>();
        entries.forEach((key, entry) -> {
            if (entry.requiresExactDisplay()) {
                result.put(key, entry);
            }
        });
        return Collections.unmodifiableMap(result);
    }

    /**
     * AE2本来のpacketと周辺MODへ渡す、負数へwrapしない互換Summary。
     *
     * <p>画面は後続ACO packetで正確値へ差し替えるため、ここでの飽和値を
     * 容量会計や実行の正本には使用しない。</p>
     */
    public CraftingPlanSummary toVanillaFacade() {
        ArrayList<CraftingPlanSummaryEntry> projected = new ArrayList<>(entries.size());
        entries.forEach((key, entry) -> projected.add(new CraftingPlanSummaryEntry(
                key,
                saturatedLong(entry.missing()),
                saturatedLong(entry.stored()),
                saturatedLong(entry.craft()))));
        Collections.sort(projected);
        return new CraftingPlanSummary(
                saturatedLong(usedBytes),
                simulation,
                List.copyOf(projected));
    }

    private static void mergeCounts(
            Map<AEKey, MutableEntry> target,
            Map<AEKey, BigInteger> counts,
            CounterTarget counterTarget,
            int maximumBits) {
        counts.forEach((key, amount) ->
                target.computeIfAbsent(key, ignored -> new MutableEntry())
                        .add(counterTarget, amount, maximumBits));
    }

    private static void mergeLongCounter(
            Map<AEKey, MutableEntry> target,
            Iterable<Object2LongMap.Entry<AEKey>> counts,
            CounterTarget counterTarget,
            int maximumBits) {
        for (Object2LongMap.Entry<AEKey> count : counts) {
            target.computeIfAbsent(count.getKey(), ignored -> new MutableEntry())
                    .add(counterTarget, BigInteger.valueOf(count.getLongValue()), maximumBits);
        }
    }

    private static void mergePatternOutputs(
            Map<AEKey, MutableEntry> target,
            Map<IPatternDetails, BigInteger> patternTimes,
            int maximumBits) {
        patternTimes.forEach((pattern, executions) -> {
            for (GenericStack output : pattern.getOutputs()) {
                BigInteger amount = BigCountMath.multiply(
                        BigInteger.valueOf(output.amount()),
                        executions,
                        "craft-confirm/pattern-output",
                        maximumBits);
                target.computeIfAbsent(output.what(), ignored -> new MutableEntry())
                        .add(CounterTarget.CRAFT, amount, maximumBits);
            }
        });
    }

    private static void mergeLongPatternOutputs(
            Map<AEKey, MutableEntry> target,
            Map<IPatternDetails, Long> patternTimes,
            int maximumBits) {
        patternTimes.forEach((pattern, executions) -> {
            for (GenericStack output : pattern.getOutputs()) {
                BigInteger amount = BigCountMath.multiply(
                        BigInteger.valueOf(output.amount()),
                        BigInteger.valueOf(executions),
                        "craft-confirm/pattern-output",
                        maximumBits);
                target.computeIfAbsent(output.what(), ignored -> new MutableEntry())
                        .add(CounterTarget.CRAFT, amount, maximumBits);
            }
        });
    }

    private static long saturatedLong(BigInteger value) {
        return value.compareTo(LONG_MAX) > 0 ? Long.MAX_VALUE : value.longValueExact();
    }

    public record Entry(
            BigInteger stored,
            BigInteger missing,
            BigInteger craft) {
        public Entry {
            Objects.requireNonNull(stored, "stored");
            Objects.requireNonNull(missing, "missing");
            Objects.requireNonNull(craft, "craft");
        }

        private void validate(String context, int maximumBits) {
            BigCountMath.requireMaximumBits(stored, context + "/stored", maximumBits);
            BigCountMath.requireMaximumBits(missing, context + "/missing", maximumBits);
            BigCountMath.requireMaximumBits(craft, context + "/craft", maximumBits);
        }

        public boolean requiresExactDisplay() {
            BigInteger requested = stored.add(missing);
            return stored.compareTo(LONG_MAX) > 0
                    || missing.compareTo(LONG_MAX) > 0
                    || craft.compareTo(LONG_MAX) > 0
                    || requested.compareTo(LONG_MAX) > 0;
        }
    }

    private enum CounterTarget {
        STORED,
        MISSING,
        EMITTED,
        CRAFT
    }

    private static final class MutableEntry {
        private BigInteger stored = BigInteger.ZERO;
        private BigInteger missing = BigInteger.ZERO;
        private BigInteger craft = BigInteger.ZERO;

        private void add(
                CounterTarget target,
                BigInteger amount,
                int maximumBits) {
            switch (target) {
                case STORED -> stored = BigCountMath.add(
                        stored, amount, "craft-confirm/stored", maximumBits);
                case MISSING -> missing = BigCountMath.add(
                        missing, amount, "craft-confirm/missing", maximumBits);
                case EMITTED -> {
                    stored = BigCountMath.add(
                            stored, amount, "craft-confirm/emitted-stored", maximumBits);
                    craft = BigCountMath.add(
                            craft, amount, "craft-confirm/emitted-craft", maximumBits);
                }
                case CRAFT -> craft = BigCountMath.add(
                        craft, amount, "craft-confirm/craft", maximumBits);
            }
        }

        private Entry freeze() {
            return new Entry(stored, missing, craft);
        }
    }
}

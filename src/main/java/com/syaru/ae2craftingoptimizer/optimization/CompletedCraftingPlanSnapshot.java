package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars;
import com.syaru.ae2craftingoptimizer.engine.WideCraftingPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** 完了計画cacheへAE2の可変KeyCounterを直接共有しないための不変値Snapshot。 */
final class CompletedCraftingPlanSnapshot {
    @Nullable
    private final WideCraftingPlan wideMetadata;
    private final GenericStack finalOutput;
    private final long bytes;
    private final boolean simulation;
    private final boolean multiplePaths;
    private final List<GenericStack> usedItems;
    private final List<GenericStack> emittedItems;
    private final List<GenericStack> missingItems;
    private final Map<IPatternDetails, Long> patternTimes;

    private CompletedCraftingPlanSnapshot(
            @Nullable WideCraftingPlan wideMetadata,
            GenericStack finalOutput,
            long bytes,
            boolean simulation,
            boolean multiplePaths,
            List<GenericStack> usedItems,
            List<GenericStack> emittedItems,
            List<GenericStack> missingItems,
            Map<IPatternDetails, Long> patternTimes) {
        this.wideMetadata = wideMetadata;
        this.finalOutput = finalOutput;
        this.bytes = bytes;
        this.simulation = simulation;
        this.multiplePaths = multiplePaths;
        this.usedItems = List.copyOf(usedItems);
        this.emittedItems = List.copyOf(emittedItems);
        this.missingItems = List.copyOf(missingItems);
        this.patternTimes = Collections.unmodifiableMap(new LinkedHashMap<>(patternTimes));
    }

    @Nullable
    static CompletedCraftingPlanSnapshot capture(ICraftingPlan plan) {
        // 外部ICraftingPlanをCraftingPlanへ変換すると固有契約を失うため、純正Facadeだけを保持する。
        if (!(plan instanceof CraftingPlan craftingPlan)) {
            return null;
        }
        return new CompletedCraftingPlanSnapshot(
                Ae2CraftingPlanSidecars.metadata(craftingPlan).orElse(null),
                copyStack(craftingPlan.finalOutput()),
                craftingPlan.bytes(),
                craftingPlan.simulation(),
                craftingPlan.multiplePaths(),
                captureCounter(craftingPlan.usedItems()),
                captureCounter(craftingPlan.emittedItems()),
                captureCounter(craftingPlan.missingItems()),
                craftingPlan.patternTimes());
    }

    CraftingPlan materialize() {
        CraftingPlan copy = new CraftingPlan(
                copyStack(finalOutput),
                bytes,
                simulation,
                multiplePaths,
                materializeCounter(usedItems),
                materializeCounter(emittedItems),
                materializeCounter(missingItems),
                new LinkedHashMap<>(patternTimes));
        // Wide simulationの正確値だけを保持し、元の可変CraftingPlanはcacheへ残さない。
        if (wideMetadata != null) {
            Ae2CraftingPlanSidecars.attach(copy, wideMetadata);
        }
        return copy;
    }

    private static List<GenericStack> captureCounter(KeyCounter counter) {
        List<GenericStack> captured = new ArrayList<>();
        // KeyCounterの正数だけを値として固定し、元Counterの後続変更から分離する。
        for (var entry : counter) {
            long amount = entry.getLongValue();
            if (amount > 0L) {
                captured.add(new GenericStack(entry.getKey(), amount));
            }
        }
        return List.copyOf(captured);
    }

    private static KeyCounter materializeCounter(List<GenericStack> stacks) {
        KeyCounter counter = new KeyCounter();
        // 呼出しごとに独立したCounterを作り、一利用者の変更を次のcache hitへ漏らさない。
        for (GenericStack stack : stacks) {
            counter.add(stack.what(), stack.amount());
        }
        return counter;
    }

    private static GenericStack copyStack(GenericStack stack) {
        return new GenericStack(stack.what(), stack.amount());
    }
}

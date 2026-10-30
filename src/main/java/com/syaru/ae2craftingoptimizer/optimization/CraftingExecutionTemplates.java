package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ICraftingInventory;
import com.google.common.collect.AbstractIterator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.level.Level;

/** One extraction's key snapshot, not a candidate, validation or stock cache. */
public final class CraftingExecutionTemplates {
    private CraftingExecutionTemplates() {}

    public static Iterable<InputTemplate> get(ICraftingInventory inventory, IPatternDetails.IInput input, Level level) {
        var possible = input.getPossibleInputs();
        if (possible.length == 1) {
            var definition = possible[0];
            var keys = new ArrayList<AEKey>(1);
            // Issue #249: finish native fuzzy enumeration before any isValid callback.
            for (AEKey key : inventory.findFuzzyTemplates(definition.what())) keys.add(key);
            return keys.isEmpty() ? List.of() : new Candidates(input, level, keys, definition.amount());
        }
        var groups = new ArrayList<Group>(possible.length);
        for (var definition : possible) {
            var keys = new ArrayList<AEKey>(1);
            for (AEKey key : inventory.findFuzzyTemplates(definition.what())) {
                keys.add(key);
            }
            if (!keys.isEmpty()) groups.add(new Group(keys, definition.amount()));
        }
        return groups.isEmpty() ? List.of() : new GroupedCandidates(input, level, groups);
    }

    private record Group(List<AEKey> keys, long amount) {}

    private record GroupedCandidates(IPatternDetails.IInput input, Level level, List<Group> groups)
            implements Iterable<InputTemplate> {
        @Override public Iterator<InputTemplate> iterator() {
            return new AbstractIterator<>() {
                private int groupIndex;
                private int keyIndex;

                @Override protected InputTemplate computeNext() {
                    // Issue #249: keep definition order and duplicates; validation remains live.
                    while (groupIndex < groups.size()) {
                        Group group = groups.get(groupIndex);
                        while (keyIndex < group.keys().size()) {
                            AEKey key = group.keys().get(keyIndex++);
                            if (input.isValid(key, level)) return new InputTemplate(key, group.amount());
                        }
                        groupIndex++;
                        keyIndex = 0;
                    }
                    return endOfData();
                }
            };
        }
    }

    private record Candidates(IPatternDetails.IInput input, Level level, List<AEKey> keys, long amount)
            implements Iterable<InputTemplate> {
        @Override public Iterator<InputTemplate> iterator() {
            var candidates = keys.iterator();
            return new AbstractIterator<>() {
                @Override protected InputTemplate computeNext() {
                    while (candidates.hasNext()) {
                        AEKey key = candidates.next();
                        if (input.isValid(key, level)) return new InputTemplate(key, amount);
                    }
                    return endOfData();
                }
            };
        }
    }
}


package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;

public final class PatternAvailabilitySorter {
    private PatternAvailabilitySorter() {
    }

    public static Collection<IPatternDetails> order(
            Collection<IPatternDetails> patterns,
            KeyCounter available) {
        if (!ACOConfig.deepPatternSelectionByAvailability()
                || patterns == null
                || patterns.size() < 2
                || patterns.size() > ACOConfig.getDeepPatternSelectionMaximumCandidates()) {
            return patterns;
        }

        var scored = new ArrayList<ScoredPattern>(patterns.size());
        int originalIndex = 0;
        for (var pattern : patterns) {
            scored.add(new ScoredPattern(pattern, score(pattern, available), originalIndex++));
        }

        scored.sort(Comparator
                .comparingLong(ScoredPattern::score)
                .reversed()
                .thenComparingInt(ScoredPattern::originalIndex));

        var result = new ArrayList<IPatternDetails>(scored.size());
        for (var entry : scored) {
            result.add(entry.pattern());
        }
        return result;
    }

    private static long score(IPatternDetails pattern, KeyCounter available) {
        long score = 0;
        for (var input : pattern.getInputs()) {
            int best = 0;
            for (GenericStack possible : input.getPossibleInputs()) {
                long stored = available.get(possible.what());
                if (stored <= 0) {
                    continue;
                }

                long required = saturatingMultiply(possible.amount(), input.getMultiplier());
                best = Math.max(best, stored >= required ? 2 : 1);
                if (best == 2) {
                    break;
                }
            }
            score += best;
        }
        return score;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0 || right <= 0) {
            return 0;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    private record ScoredPattern(IPatternDetails pattern, long score, int originalIndex) {
    }
}

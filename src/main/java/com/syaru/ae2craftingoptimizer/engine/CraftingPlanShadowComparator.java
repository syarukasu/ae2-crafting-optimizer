package com.syaru.ae2craftingoptimizer.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CraftingPlanShadowComparator {
    private CraftingPlanShadowComparator() {
    }

    public static <K> ShadowComparison compare(
            LongCraftingPlan<K> shadow,
            Map<String, Long> referencePatternExecutions,
            Map<K, Long> referenceMissing) {
        Objects.requireNonNull(shadow, "shadow");
        List<String> mismatches = new ArrayList<>();
        compareMap("pattern executions", shadow.patternExecutions(), referencePatternExecutions, mismatches);
        compareMap("missing inputs", shadow.missing(), referenceMissing, mismatches);
        return new ShadowComparison(mismatches.isEmpty(), List.copyOf(mismatches));
    }

    private static <K> void compareMap(
            String label, Map<K, Long> shadow, Map<K, Long> reference, List<String> mismatches) {
        Objects.requireNonNull(reference, "reference");
        if (!shadow.equals(reference)) {
            mismatches.add(label + " differ: shadow=" + shadow + ", reference=" + reference);
        }
    }

    public record ShadowComparison(boolean matches, List<String> mismatches) {
    }
}

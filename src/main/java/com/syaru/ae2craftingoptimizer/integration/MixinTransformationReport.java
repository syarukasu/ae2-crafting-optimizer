package com.syaru.ae2craftingoptimizer.integration;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records which configured transformation was selected before the first server job. */
public final class MixinTransformationReport {
    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    private MixinTransformationReport() {
    }

    public static void record(
            String feature,
            String dependency,
            String dependencyVersion,
            String targetClass,
            String mixinClass,
            boolean applied,
            boolean failClosed) {
        ENTRIES.add(new Entry(
                feature,
                dependency,
                dependencyVersion,
                targetClass,
                mixinClass,
                applied,
                failClosed));
    }

    public static List<Entry> snapshot() {
        return List.copyOf(ENTRIES);
    }

    public static void log() {
        List<Entry> entries = new ArrayList<>(ENTRIES);
        entries.sort(Comparator
                .comparing(Entry::feature)
                .thenComparing(Entry::mixinClass)
                .thenComparing(Entry::targetClass));
        AE2CraftingOptimizer.LOGGER.info(
                "ACO Mixin transformation report: {} selected entries",
                entries.size());
        for (Entry entry : entries) {
            AE2CraftingOptimizer.LOGGER.info(
                    "ACO Mixin: feature={}, target={}, mixin={}, applied={}, dependency={} {}, policy={}",
                    entry.feature(),
                    entry.targetClass(),
                    entry.mixinClass(),
                    entry.applied(),
                    entry.dependency(),
                    entry.dependencyVersion(),
                    entry.failClosed() ? "fail-closed" : "fail-open");
        }
    }

    public record Entry(
            String feature,
            String dependency,
            String dependencyVersion,
            String targetClass,
            String mixinClass,
            boolean applied,
            boolean failClosed) {
    }
}

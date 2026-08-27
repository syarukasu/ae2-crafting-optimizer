package com.syaru.ae2craftingoptimizer.optimization;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Pattern世代とrecipe世代を一つの境界として扱う、固定上限のaccess-order cache。 */
final class GenerationScopedDecisionCache<K, V> {
    /** Java LinkedHashMapの既定値と同じ負荷率を使う。 */
    private static final float LRU_LOAD_FACTOR = 0.75F;
    private final int maximumEntries;
    private final Map<K, V> values;
    private long patternGeneration = Long.MIN_VALUE;
    private long recipeGeneration = Long.MIN_VALUE;

    GenerationScopedDecisionCache(int maximumEntries) {
        // 0件cacheは全呼出をmissにするため、呼出側Configが0でも最小1件へ正規化する。
        if (maximumEntries <= 0) {
            throw new IllegalArgumentException("maximumEntries must be positive");
        }
        this.maximumEntries = maximumEntries;
        this.values = new LinkedHashMap<>(
                Math.min(16, maximumEntries),
                LRU_LOAD_FACTOR,
                true);
    }

    Lookup<V> getOrCompute(
            K key,
            long expectedPatternGeneration,
            long expectedRecipeGeneration,
            BooleanSupplier generationStillCurrent,
            Supplier<CompiledValue<V>> compiler) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(generationStillCurrent, "generationStillCurrent");
        Objects.requireNonNull(compiler, "compiler");
        synchronized (this) {
            // 世代が一方でも変われば、旧Pattern参照を一件も再利用しない。
            if (patternGeneration != expectedPatternGeneration
                    || recipeGeneration != expectedRecipeGeneration) {
                values.clear();
                patternGeneration = expectedPatternGeneration;
                recipeGeneration = expectedRecipeGeneration;
            }
            V cached = values.get(key);
            // null値は保存しない契約なので、非nullなら確実なcache hitである。
            if (cached != null) {
                return new Lookup<>(cached, true);
            }
        }

        // metadata取得はservice呼出を含むため、別rootの冷間compileをlock内で直列化しない。
        CompiledValue<V> compiled = Objects.requireNonNull(compiler.get(), "compiled value");
        V value = Objects.requireNonNull(compiled.value(), "compiled value payload");
        // 動的Patternの結果は呼出元へ返すだけで、次注文へ共有しない。
        if (!compiled.cacheable() || !generationStillCurrent.getAsBoolean()) {
            return new Lookup<>(value, false);
        }

        synchronized (this) {
            /*
             * compile中に世代が変わった値は公開しない。別threadが同じProgramを先に
             * 公開した場合は、その世代検証済み値を優先して重複結果を捨てる。
             */
            if (patternGeneration != expectedPatternGeneration
                    || recipeGeneration != expectedRecipeGeneration
                    || !generationStillCurrent.getAsBoolean()) {
                return new Lookup<>(value, false);
            }
            V concurrent = values.get(key);
            if (concurrent != null) {
                return new Lookup<>(concurrent, true);
            }
            // 固定上限へ達した時だけ、access-order先頭の最古Programを一件除去する。
            if (values.size() >= maximumEntries) {
                Iterator<K> iterator = values.keySet().iterator();
                // sizeが上限以上なら通常は要素があるが、空iteratorは進めない。
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
            values.put(key, value);
        }
        return new Lookup<>(value, false);
    }

    record CompiledValue<V>(V value, boolean cacheable) {
    }

    record Lookup<V>(V value, boolean hit) {
    }
}

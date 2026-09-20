package com.syaru.ae2craftingoptimizer.engine;

import java.util.List;
import java.util.function.Function;

/** Request-owned input observations; no execution or inventory ownership. */
interface BranchingInputRules<K> {
    Input<K> input(CompiledPattern<K> pattern, int slot);
    KeyIndex<K> newIndex();
    List<K> storedFuzzyKeys(K key);

    record Template<K>(K key, long amount) { }
    record Observation<K>(boolean valid, K remainder) { }
    record Input<K>(List<Template<K>> templates, K craftedKey, boolean emittable,
            Function<K, Observation<K>> observe) {
        public Input { templates = List.copyOf(templates); }
    }

    /** Membership/order only. BigInteger quantities never pass through this index. */
    interface KeyIndex<K> {
        void add(K key);
        List<K> fuzzy(K key);
        List<K> keys();
    }
}

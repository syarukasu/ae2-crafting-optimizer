package com.syaru.ae2craftingoptimizer.optimization;

import java.util.function.IntFunction;

/** 設定Inventoryの世代とサイズに結び付いた、nullも保持できる固定長slot cache。 */
public final class GenerationSlotCache<T> {
    private long generation = Long.MIN_VALUE;
    private Object[] values = new Object[0];
    private boolean[] initialized = new boolean[0];

    public T get(long currentGeneration, int slotCount, int slot, IntFunction<T> loader) {
        // 不正slotはcacheへ入れず、AE2本来のloaderへそのまま委ねる。
        if (slot < 0 || slot >= slotCount) {
            return loader.apply(slot);
        }
        // 設定変更またはスロット数変更時だけ全候補を破棄する。
        if (generation != currentGeneration || values.length != slotCount) {
            generation = currentGeneration;
            values = new Object[slotCount];
            initialized = new boolean[slotCount];
        }
        // null設定も有効な結果なので、別の初期化bitで未読と区別する。
        if (!initialized[slot]) {
            values[slot] = loader.apply(slot);
            initialized[slot] = true;
        }
        @SuppressWarnings("unchecked")
        T value = (T) values[slot];
        return value;
    }
}

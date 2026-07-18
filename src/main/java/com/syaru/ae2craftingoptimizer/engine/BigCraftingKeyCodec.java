package com.syaru.ae2craftingoptimizer.engine;

import net.minecraft.nbt.CompoundTag;

public interface BigCraftingKeyCodec<K> {
    CompoundTag encode(K key);

    K decode(CompoundTag tag);
}

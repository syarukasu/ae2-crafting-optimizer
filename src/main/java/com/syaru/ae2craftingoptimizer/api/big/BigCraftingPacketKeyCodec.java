package com.syaru.ae2craftingoptimizer.api.big;

import net.minecraft.network.FriendlyByteBuf;

/** Packet codec supplied by the CPU add-on for its crafting key type. */
public interface BigCraftingPacketKeyCodec<K> {
    void write(FriendlyByteBuf buffer, K key);

    K read(FriendlyByteBuf buffer);
}

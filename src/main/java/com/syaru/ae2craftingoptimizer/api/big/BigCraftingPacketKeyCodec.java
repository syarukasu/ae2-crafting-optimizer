package com.syaru.ae2craftingoptimizer.api.big;

import net.minecraft.network.RegistryFriendlyByteBuf;

/** Packet codec supplied by the CPU add-on for its crafting key type. */
public interface BigCraftingPacketKeyCodec<K> {
    void write(RegistryFriendlyByteBuf buffer, K key);

    K read(RegistryFriendlyByteBuf buffer);
}

package com.syaru.ae2craftingoptimizer.api.big;

import appeng.api.stacks.AEKey;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** AE2 item/fluid/chemical key codec for bounded BigInteger status pages. */
public final class AeKeyBigCraftingPacketCodec implements BigCraftingPacketKeyCodec<AEKey> {
    public static final AeKeyBigCraftingPacketCodec INSTANCE = new AeKeyBigCraftingPacketCodec();

    private AeKeyBigCraftingPacketCodec() {
    }

    @Override
    public void write(RegistryFriendlyByteBuf buffer, AEKey key) {
        AEKey.writeKey(buffer, key);
    }

    @Override
    public AEKey read(RegistryFriendlyByteBuf buffer) {
        return AEKey.readKey(buffer);
    }
}

package com.syaru.ae2craftingoptimizer.api.big;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingKeyCodec;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/** Lossless item/fluid/chemical key codec for persistent BigInteger job state. */
public final class AeKeyBigCraftingCodec implements BigCraftingKeyCodec<AEKey> {
    public static final AeKeyBigCraftingCodec INSTANCE = new AeKeyBigCraftingCodec();

    private AeKeyBigCraftingCodec() {
    }

    @Override
    public CompoundTag encode(AEKey key) {
        return Objects.requireNonNull(key, "key").toTagGeneric();
    }

    @Override
    public AEKey decode(CompoundTag tag) {
        AEKey key = AEKey.fromTagGeneric(Objects.requireNonNull(tag, "tag"));
        if (key == null) {
            throw new IllegalArgumentException("saved BigInteger crafting key is unavailable or malformed");
        }
        return key;
    }
}

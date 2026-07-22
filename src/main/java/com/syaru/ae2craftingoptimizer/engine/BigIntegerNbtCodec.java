package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class BigIntegerNbtCodec {
    private BigIntegerNbtCodec() {
    }

    public static void putNonNegative(
            CompoundTag tag,
            String key,
            BigInteger value,
            int maximumBits) {
        validateMaximum(maximumBits);
        BigCountMath.requireMaximumBits(value, key, maximumBits);
        tag.putByteArray(key, value.toByteArray());
    }

    public static BigInteger getNonNegative(
            CompoundTag tag,
            String key,
            int maximumBits) {
        validateMaximum(maximumBits);
        // byte array以外の型や欠落値を0として黙認せず、破損保存として拒否する。
        if (!tag.contains(key, Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalArgumentException("missing BigInteger byte array " + key);
        }
        byte[] encoded = tag.getByteArray(key);
        int maximumBytes = Math.addExact(maximumBits, 8) / 8;
        // 空配列または設定上限を越える保存値はBigInteger生成前に拒否する。
        if (encoded.length == 0 || encoded.length > maximumBytes) {
            throw new IllegalArgumentException("invalid or oversized BigInteger byte array " + key);
        }
        BigInteger value = new BigInteger(encoded);
        BigCountMath.requireMaximumBits(value, key, maximumBits);
        // 冗長な符号byteを含まないcanonical表現だけを受け入れる。
        if (!Arrays.equals(encoded, value.toByteArray())) {
            throw new IllegalArgumentException("non-canonical BigInteger byte array " + key);
        }
        return value;
    }

    private static void validateMaximum(int maximumBits) {
        BigCountMath.requireMaximumBits(BigInteger.ZERO, "NBT maximum", maximumBits);
    }
}

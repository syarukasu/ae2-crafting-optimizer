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
        BigCountMath.requireNonNegative(value, key);
        if (value.bitLength() > maximumBits) {
            throw new IllegalArgumentException(key + " exceeds " + maximumBits + " bits");
        }
        tag.putByteArray(key, value.toByteArray());
    }

    public static BigInteger getNonNegative(
            CompoundTag tag,
            String key,
            int maximumBits) {
        validateMaximum(maximumBits);
        if (!tag.contains(key, Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalArgumentException("missing BigInteger byte array " + key);
        }
        byte[] encoded = tag.getByteArray(key);
        int maximumBytes = Math.addExact(maximumBits, 8) / 8;
        if (encoded.length == 0 || encoded.length > maximumBytes) {
            throw new IllegalArgumentException("invalid or oversized BigInteger byte array " + key);
        }
        BigInteger value = new BigInteger(encoded);
        BigCountMath.requireNonNegative(value, key);
        if (!Arrays.equals(encoded, value.toByteArray())) {
            throw new IllegalArgumentException("non-canonical BigInteger byte array " + key);
        }
        if (value.bitLength() > maximumBits) {
            throw new IllegalArgumentException(key + " exceeds " + maximumBits + " bits");
        }
        return value;
    }

    private static void validateMaximum(int maximumBits) {
        if (maximumBits < 1 || maximumBits > 1_048_576) {
            throw new IllegalArgumentException("maximumBits must be between 1 and 1048576");
        }
    }
}

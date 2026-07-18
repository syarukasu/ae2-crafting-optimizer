package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.Arrays;
import net.minecraft.network.FriendlyByteBuf;

public final class BigIntegerBufferCodec {
    public static final int PROTOCOL_VERSION = 1;
    public static final int HARD_MAXIMUM_BYTES = 131_073;

    private BigIntegerBufferCodec() {
    }

    public static void writeNonNegative(
            FriendlyByteBuf buffer,
            BigInteger value,
            int maximumBits) {
        BigCountMath.requireNonNegative(value, "packet/value");
        validateMaximum(maximumBits);
        if (value.bitLength() > maximumBits) {
            throw new IllegalArgumentException("BigInteger packet value exceeds " + maximumBits + " bits");
        }
        byte[] encoded = value.toByteArray();
        if (encoded.length > HARD_MAXIMUM_BYTES) {
            throw new IllegalArgumentException("BigInteger packet value exceeds hard byte cap");
        }
        buffer.writeVarInt(encoded.length);
        buffer.writeBytes(encoded);
    }

    public static BigInteger readNonNegative(FriendlyByteBuf buffer, int maximumBits) {
        validateMaximum(maximumBits);
        int length = buffer.readVarInt();
        int maximumBytes = Math.addExact(maximumBits, 8) / 8;
        if (length < 1 || length > maximumBytes || length > HARD_MAXIMUM_BYTES) {
            throw new IllegalArgumentException("invalid BigInteger packet length " + length);
        }
        byte[] encoded = new byte[length];
        buffer.readBytes(encoded);
        BigInteger value = new BigInteger(encoded);
        BigCountMath.requireNonNegative(value, "packet/value");
        if (!Arrays.equals(encoded, value.toByteArray())) {
            throw new IllegalArgumentException("non-canonical BigInteger packet value");
        }
        if (value.bitLength() > maximumBits) {
            throw new IllegalArgumentException("BigInteger packet value exceeds " + maximumBits + " bits");
        }
        return value;
    }

    public static void requireProtocol(int remoteVersion) {
        if (remoteVersion != PROTOCOL_VERSION) {
            throw new IllegalStateException(
                    "ACO BigInteger protocol mismatch: local " + PROTOCOL_VERSION + ", remote " + remoteVersion);
        }
    }

    private static void validateMaximum(int maximumBits) {
        if (maximumBits < 1 || maximumBits > 1_048_576) {
            throw new IllegalArgumentException("maximumBits must be between 1 and 1048576");
        }
    }
}

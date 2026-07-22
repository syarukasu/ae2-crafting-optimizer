package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.Arrays;
import net.minecraft.network.FriendlyByteBuf;

public final class BigIntegerBufferCodec {
    public static final int PROTOCOL_VERSION = 1;
    /** 16,384桁の正数と符号byteを格納できる最大packet長。 */
    public static final int HARD_MAXIMUM_BYTES = (BigCountMath.HARD_MAXIMUM_BITS + Byte.SIZE) / Byte.SIZE;

    private BigIntegerBufferCodec() {
    }

    public static void writeNonNegative(
            FriendlyByteBuf buffer,
            BigInteger value,
            int maximumBits) {
        validateMaximum(maximumBits);
        BigCountMath.requireMaximumBits(value, "packet/value", maximumBits);
        byte[] encoded = value.toByteArray();
        // 設定値とは別の固定byte上限でも、巨大packetの確保を防ぐ。
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
        // 負数、空配列、設定上限、実装上限のいずれかに反する長さは読み込まない。
        if (length < 1 || length > maximumBytes || length > HARD_MAXIMUM_BYTES) {
            throw new IllegalArgumentException("invalid BigInteger packet length " + length);
        }
        byte[] encoded = new byte[length];
        buffer.readBytes(encoded);
        BigInteger value = new BigInteger(encoded);
        BigCountMath.requireMaximumBits(value, "packet/value", maximumBits);
        // 同じ数値を複数のbyte列で表せないよう、BigInteger標準のcanonical表現だけを受け入れる。
        if (!Arrays.equals(encoded, value.toByteArray())) {
            throw new IllegalArgumentException("non-canonical BigInteger packet value");
        }
        return value;
    }

    public static void requireProtocol(int remoteVersion) {
        // 異なるpacket schema同士を誤読しないよう、protocol不一致は明示的に拒否する。
        if (remoteVersion != PROTOCOL_VERSION) {
            throw new IllegalStateException(
                    "ACO BigInteger protocol mismatch: local " + PROTOCOL_VERSION + ", remote " + remoteVersion);
        }
    }

    private static void validateMaximum(int maximumBits) {
        BigCountMath.requireMaximumBits(BigInteger.ZERO, "packet maximum", maximumBits);
    }
}

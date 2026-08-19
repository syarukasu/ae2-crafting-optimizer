package com.syaru.ae2craftingoptimizer.api.contract;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class CanonicalBigIntegerCodecTest {
    private static final ExactCountLimits LIMITS = new ExactCountLimits(16, 2, 8, 4096, 32, 8);

    @Test
    void encodesZeroAndPositiveValuesWithoutSignPadding() {
        assertArrayEquals(new byte[] {0}, CanonicalBigIntegerCodec.encodeNonNegative(BigInteger.ZERO, LIMITS));
        assertArrayEquals(new byte[] {(byte) 0xff},
                CanonicalBigIntegerCodec.encodeNonNegative(BigInteger.valueOf(255), LIMITS));
        assertEquals(BigInteger.valueOf(255),
                CanonicalBigIntegerCodec.decodeNonNegative(new byte[] {(byte) 0xff}, LIMITS));
    }

    @Test
    void rejectsNegativeNonCanonicalAndOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalBigIntegerCodec.encodeNonNegative(BigInteger.valueOf(-1), LIMITS));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalBigIntegerCodec.decodeNonNegative(new byte[] {0, 1}, LIMITS));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalBigIntegerCodec.encodeNonNegative(BigInteger.ONE.shiftLeft(16), LIMITS));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalBigIntegerCodec.encodePositive(BigInteger.ZERO, LIMITS));
    }

    @Test
    void exactConversionAndBitBoundariesAreChecked() {
        BigInteger maximum = BigInteger.ONE.shiftLeft(LIMITS.maximumCountBits()).subtract(BigInteger.ONE);
        assertEquals(maximum,
                CanonicalBigIntegerCodec.decodeNonNegative(
                        CanonicalBigIntegerCodec.encodeNonNegative(maximum, LIMITS), LIMITS));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalBigIntegerCodec.encodeNonNegative(
                        BigInteger.ONE.shiftLeft(LIMITS.maximumCountBits()), LIMITS));
        assertEquals(255L, CanonicalBigIntegerCodec.toLongExact(BigInteger.valueOf(255), LIMITS));
        assertThrows(ArithmeticException.class,
                () -> CanonicalBigIntegerCodec.toLongExact(
                        BigInteger.ONE.shiftLeft(63), ExactCountLimits.defaults()));
    }

    @Test
    void nbtRoundTripAndLegacyMigrationAreExact() {
        CompoundTag tag = new CompoundTag();
        BigInteger value = BigInteger.ONE.shiftLeft(15).add(BigInteger.valueOf(7));
        CanonicalBigIntegerCodec.writeNonNegative(tag, "count", value, LIMITS);
        assertEquals(value, CanonicalBigIntegerCodec.readNonNegative(tag, "count", LIMITS));

        CompoundTag legacy = new CompoundTag();
        legacy.putString("count", value.toString());
        assertEquals(value, CanonicalBigIntegerCodec.readNonNegative(legacy, "count", LIMITS));
        CanonicalBigIntegerCodec.writeNonNegative(legacy, "count", value, LIMITS);
        assertEquals(value, CanonicalBigIntegerCodec.readNonNegative(legacy, "count", LIMITS));
    }

    @Test
    void unknownAndMixedNbtSchemasFailClosed() {
        CompoundTag unknown = new CompoundTag();
        unknown.putInt("count_schema", 99);
        unknown.putByteArray("count_bytes", new byte[] {1});
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalBigIntegerCodec.readNonNegative(unknown, "count", LIMITS));

        CompoundTag mixed = new CompoundTag();
        mixed.putString("count", "1");
        mixed.putInt("count_schema", 1);
        mixed.putByteArray("count_bytes", new byte[] {1});
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalBigIntegerCodec.readNonNegative(mixed, "count", LIMITS));
    }
}

package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class BigIntegerNbtCodecTest {
    @Test
    void roundTripsZeroLongBoundaryAndConfiguredDecimalMagnitudes() {
        for (BigInteger value : new BigInteger[] {
                BigInteger.ZERO,
                BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.TEN.pow(64).subtract(BigInteger.ONE),
                BigInteger.TEN.pow(128).subtract(BigInteger.ONE),
                BigInteger.TEN.pow(1024).subtract(BigInteger.ONE)
        }) {
            CompoundTag tag = new CompoundTag();
            BigIntegerNbtCodec.putNonNegative(tag, "value", value, 4096);
            assertEquals(value, BigIntegerNbtCodec.getNonNegative(tag, "value", 4096));
        }
    }

    @Test
    void rejectsNegativeAndOversizedPayloads() {
        CompoundTag tag = new CompoundTag();
        assertThrows(IllegalArgumentException.class, () ->
                BigIntegerNbtCodec.putNonNegative(tag, "value", BigInteger.valueOf(-1L), 128));

        tag.putByteArray("value", BigInteger.ONE.shiftLeft(1024).toByteArray());
        assertThrows(IllegalArgumentException.class, () ->
                BigIntegerNbtCodec.getNonNegative(tag, "value", 128));

        tag.putByteArray("value", new byte[] {0, 1});
        assertThrows(IllegalArgumentException.class, () ->
                BigIntegerNbtCodec.getNonNegative(tag, "value", 128));
    }
}

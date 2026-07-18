package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class BigIntegerBufferCodecTest {
    @Test
    void roundTripsAThousandDecimalDigits() {
        BigInteger value = BigInteger.TEN.pow(1023).add(BigInteger.valueOf(12345));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        BigIntegerBufferCodec.writeNonNegative(buffer, value, 4096);
        assertEquals(value, BigIntegerBufferCodec.readNonNegative(buffer, 4096));
    }

    @Test
    void rejectsProtocolMismatch() {
        assertThrows(
                IllegalStateException.class,
                () -> BigIntegerBufferCodec.requireProtocol(BigIntegerBufferCodec.PROTOCOL_VERSION + 1));
    }

    @Test
    void rejectsNegativeAndOversizedEncodedValues() {
        FriendlyByteBuf negative = new FriendlyByteBuf(Unpooled.buffer());
        negative.writeVarInt(1);
        negative.writeByte(0xFF);
        assertThrows(IllegalArgumentException.class, () ->
                BigIntegerBufferCodec.readNonNegative(negative, 64));

        FriendlyByteBuf oversized = new FriendlyByteBuf(Unpooled.buffer());
        oversized.writeVarInt(100);
        assertThrows(IllegalArgumentException.class, () ->
                BigIntegerBufferCodec.readNonNegative(oversized, 64));

        FriendlyByteBuf nonCanonical = new FriendlyByteBuf(Unpooled.buffer());
        nonCanonical.writeVarInt(2);
        nonCanonical.writeBytes(new byte[] {0, 1});
        assertThrows(IllegalArgumentException.class, () ->
                BigIntegerBufferCodec.readNonNegative(nonCanonical, 64));
    }
}

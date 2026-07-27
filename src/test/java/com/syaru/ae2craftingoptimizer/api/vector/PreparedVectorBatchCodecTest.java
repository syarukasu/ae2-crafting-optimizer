package com.syaru.ae2craftingoptimizer.api.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class PreparedVectorBatchCodecTest {
    private static final BigInteger HUGE_AMOUNT =
            BigInteger.TEN.pow(16_384).subtract(BigInteger.ONE);

    @Test
    void preservesTheConfiguredSixteenThousandDigitMagnitude() {
        CompoundTag tag = new CompoundTag();

        PreparedVectorBatchCodec.putNonNegative(
                tag,
                "amount",
                HUGE_AMOUNT);

        assertEquals(
                HUGE_AMOUNT,
                PreparedVectorBatchCodec.readNonNegative(
                        tag,
                        "amount"));
    }

    @Test
    void migratesLegacyReceiptToNetworkStorageOwnership() {
        CompoundTag legacy = new CompoundTag();

        assertEquals(
                VectorResourceMode.NETWORK_STORAGE,
                PreparedVectorBatchCodec.readResourceMode(1, legacy));
    }

    @Test
    void preservesTheExplicitHostEscrowModeInSchemaTwo() {
        CompoundTag current = new CompoundTag();
        current.putString(
                "resourceMode",
                VectorResourceMode.HOST_ESCROWED.name());

        assertEquals(
                VectorResourceMode.HOST_ESCROWED,
                PreparedVectorBatchCodec.readResourceMode(2, current));
    }

    @Test
    void rejectsNonCanonicalOrOversizedIntegerPayloads() {
        CompoundTag negative = new CompoundTag();
        negative.putByteArray("amount", new byte[] {(byte) 0xFF});
        CompoundTag oversized = new CompoundTag();
        // Codec上限8,192 byteを一byteだけ超える破損Payloadを用意する。
        oversized.putByteArray("amount", new byte[8_193]);

        assertThrows(
                IllegalArgumentException.class,
                () -> PreparedVectorBatchCodec.readNonNegative(
                        negative,
                        "amount"));
        assertThrows(
                IllegalArgumentException.class,
                () -> PreparedVectorBatchCodec.readNonNegative(
                        oversized,
                        "amount"));
    }
}

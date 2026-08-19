package com.syaru.ae2craftingoptimizer.api.contract;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ExactCountPayloadCodecTest {
    private static final ExactCountLimits LIMITS = new ExactCountLimits(32, 4, 4, 4096, 32, 8);

    @Test
    void allPayloadKindsUseOneCanonicalDeterministicCodec() {
        Map<String, BigInteger> reverseOrder = new LinkedHashMap<>();
        reverseOrder.put("minecraft:zinc", BigInteger.valueOf(9));
        reverseOrder.put("minecraft:iron", BigInteger.ONE);
        ExactCountPayload first = ExactCountPayload.of(
                PayloadKind.REQUEST, "tx-1", reverseOrder, new byte[] {1, 2}, LIMITS);
        Map<String, BigInteger> sortedOrder = new LinkedHashMap<>();
        sortedOrder.put("minecraft:iron", BigInteger.ONE);
        sortedOrder.put("minecraft:zinc", BigInteger.valueOf(9));
        ExactCountPayload second = ExactCountPayload.of(
                PayloadKind.REQUEST, "tx-1", sortedOrder, new byte[] {1, 2}, LIMITS);

        byte[] encoded = ExactCountPayloadCodec.encode(first, LIMITS);
        assertArrayEquals(encoded, ExactCountPayloadCodec.encode(second, LIMITS));
        ExactCountPayload decoded = ExactCountPayloadCodec.decode(encoded, LIMITS);
        assertEquals(first, decoded);
        assertEquals(PayloadKind.REQUEST, decoded.kind());
        assertEquals(sortedOrder, decoded.counts());
        assertArrayEquals(new byte[] {1, 2}, decoded.digest());
    }

    @Test
    void payloadNbtRoundTripUsesTheSameCodec() {
        ExactCountPayload payload = ExactCountPayload.of(
                PayloadKind.RECEIPT,
                "receipt-1",
                Map.of("minecraft:iron", BigInteger.ONE.shiftLeft(31)),
                new byte[] {7},
                LIMITS);
        CompoundTag tag = new CompoundTag();
        ExactCountPayloadCodec.writeToNbt(tag, "payload", payload, LIMITS);
        ExactCountPayload restored = ExactCountPayloadCodec.readFromNbt(tag, "payload", LIMITS);
        assertEquals(payload.kind(), restored.kind());
        assertEquals(payload.identifier(), restored.identifier());
        assertEquals(payload.counts(), restored.counts());
        assertArrayEquals(payload.digest(), restored.digest());
    }

    @Test
    void rejectsNonCanonicalOrderTrailingDataAndLimits() {
        ExactCountPayload payload = ExactCountPayload.of(
                PayloadKind.HOST, "host-1", Map.of("minecraft:iron", BigInteger.ONE), new byte[0], LIMITS);
        byte[] encoded = ExactCountPayloadCodec.encode(payload, LIMITS);
        byte[] trailing = java.util.Arrays.copyOf(encoded, encoded.length + 1);
        trailing[trailing.length - 1] = 1;
        assertThrows(IllegalArgumentException.class,
                () -> ExactCountPayloadCodec.decode(trailing, LIMITS));

        ExactCountLimits oneKey = new ExactCountLimits(32, 4, 1, 4096, 32, 8);
        assertThrows(IllegalArgumentException.class,
                () -> ExactCountPayload.of(
                        PayloadKind.JOURNAL,
                        "journal-1",
                        Map.of("a", BigInteger.ONE, "b", BigInteger.TWO),
                        new byte[0],
                        oneKey));
    }
}

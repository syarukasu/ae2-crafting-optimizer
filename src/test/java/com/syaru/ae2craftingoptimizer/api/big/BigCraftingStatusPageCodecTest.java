package com.syaru.ae2craftingoptimizer.api.big;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.syaru.ae2craftingoptimizer.engine.BigCraftingJob;
import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class BigCraftingStatusPageCodecTest {
    private static final BigCraftingPacketKeyCodec<String> STRINGS = new BigCraftingPacketKeyCodec<>() {
        @Override
        public void write(FriendlyByteBuf buffer, String key) {
            buffer.writeUtf(key, 64);
        }

        @Override
        public String read(FriendlyByteBuf buffer) {
            return buffer.readUtf(64);
        }
    };

    @Test
    void roundTripsBoundedBigIntegerStatusPage() {
        BigInteger capacity = BigInteger.TEN.pow(127);
        var page = new BigCraftingStatusPage<>(
                UUID.randomUUID(),
                capacity,
                BigInteger.TEN,
                capacity.subtract(BigInteger.TEN),
                1,
                0,
                List.of(new BigCraftingStatusPage.JobSummary<>(
                        UUID.randomUUID(),
                        "diamond_block_x9",
                        BigInteger.TEN.pow(100),
                        BigInteger.TEN,
                        BigInteger.TEN.pow(99),
                        BigInteger.ONE,
                        9,
                        1,
                        BigCraftingJob.State.RUNNING,
                        true)));
        BigCraftingStatusPageCodec<String> codec = new BigCraftingStatusPageCodec<>(STRINGS, 512, 16);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        codec.write(buffer, page);
        assertEquals(page, codec.read(buffer));
    }

    @Test
    void rejectsProtocolMismatchAndOversizedPages() {
        BigCraftingStatusPageCodec<String> codec = new BigCraftingStatusPageCodec<>(STRINGS, 128, 1);
        FriendlyByteBuf wrongProtocol = new FriendlyByteBuf(Unpooled.buffer());
        wrongProtocol.writeVarInt(BigCraftingStatusPageCodec.PROTOCOL_VERSION + 1);
        assertThrows(IllegalStateException.class, () -> codec.read(wrongProtocol));

        BigCraftingStatusPage<String> oversized = new BigCraftingStatusPage<>(
                UUID.randomUUID(),
                BigInteger.ONE,
                BigInteger.ZERO,
                BigInteger.ONE,
                2,
                0,
                List.of(summary(), summary()));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.write(new FriendlyByteBuf(Unpooled.buffer()), oversized));
    }

    @Test
    void rejectsAByteOversizedPageEvenWhenItsEntryCountIsValid() {
        BigInteger huge = com.syaru.ae2craftingoptimizer.engine.BigCountMath.hardMaximumValue();
        var hugeSummary = new BigCraftingStatusPage.JobSummary<>(
                UUID.randomUUID(),
                "key",
                huge,
                BigInteger.ZERO,
                huge,
                huge,
                1,
                1,
                BigCraftingJob.State.RUNNING,
                false);
        int entriesNeededToExceedOneMiB = 64;
        var summaries = java.util.Collections.nCopies(entriesNeededToExceedOneMiB, hugeSummary);
        var page = new BigCraftingStatusPage<>(
                UUID.randomUUID(),
                huge,
                BigInteger.ZERO,
                huge,
                entriesNeededToExceedOneMiB,
                0,
                summaries);
        var codec = new BigCraftingStatusPageCodec<String>(
                STRINGS,
                com.syaru.ae2craftingoptimizer.engine.BigCountMath.HARD_MAXIMUM_BITS,
                entriesNeededToExceedOneMiB);

        assertThrows(
                BigCraftingStatusPageCodec.PacketTooLargeException.class,
                () -> codec.write(new FriendlyByteBuf(Unpooled.buffer()), page));
    }

    private static BigCraftingStatusPage.JobSummary<String> summary() {
        return new BigCraftingStatusPage.JobSummary<>(
                UUID.randomUUID(),
                "key",
                BigInteger.ONE,
                BigInteger.ZERO,
                BigInteger.ONE,
                BigInteger.ZERO,
                1,
                0,
                BigCraftingJob.State.PLANNED,
                false);
    }
}

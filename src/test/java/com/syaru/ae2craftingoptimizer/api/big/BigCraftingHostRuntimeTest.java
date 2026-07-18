package com.syaru.ae2craftingoptimizer.api.big;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syaru.ae2craftingoptimizer.engine.BigCraftingJob;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingKeyCodec;
import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class BigCraftingHostRuntimeTest {
    private static final BigCraftingKeyCodec<String> STRINGS = new BigCraftingKeyCodec<>() {
        @Override
        public CompoundTag encode(String key) {
            CompoundTag tag = new CompoundTag();
            tag.putString("value", key);
            return tag;
        }

        @Override
        public String decode(CompoundTag tag) {
            return tag.getString("value");
        }
    };

    @Test
    void sharesOneCapacityBetweenNormalAndBigJobs() {
        BigCraftingHostRuntime<String> host = host(BigInteger.valueOf(1_000));
        UUID normalJob = UUID.randomUUID();
        assertTrue(host.reserveExternal(normalJob, BigInteger.valueOf(400)));
        assertTrue(host.submit(job(BigInteger.valueOf(500))));

        assertEquals(BigInteger.valueOf(900), host.reserved());
        assertEquals(BigInteger.valueOf(100), host.available());
        assertFalse(host.reserveExternal(UUID.randomUUID(), BigInteger.valueOf(101)));
    }

    @Test
    void capacityShrinkKeepsExistingJobsButBlocksNewReservations() {
        BigCraftingHostRuntime<String> host = host(BigInteger.valueOf(1_000));
        UUID normalJob = UUID.randomUUID();
        assertTrue(host.reserveExternal(normalJob, BigInteger.valueOf(800)));

        host.resizePhysicalCapacity(BigInteger.valueOf(500));

        assertTrue(host.isOvercommitted());
        assertEquals(BigInteger.ZERO, host.available());
        assertEquals(BigInteger.valueOf(800), host.externalReserved());
        assertFalse(host.reserveExternal(UUID.randomUUID(), BigInteger.ONE));

        host.releaseExternal(normalJob);
        assertFalse(host.isOvercommitted());
        assertEquals(BigInteger.valueOf(500), host.available());
    }

    @Test
    void authoritativeReservationReplacementIsAtomicAndPersistent() {
        BigInteger capacity = BigInteger.TEN.pow(64);
        BigCraftingHostRuntime<String> host = host(capacity);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        host.replaceExternalReservations(Map.of(
                first, BigInteger.valueOf(Long.MAX_VALUE),
                second, BigInteger.valueOf(Long.MAX_VALUE)));

        BigCraftingHostRuntime<String> restored = BigCraftingHostRuntime.load(
                host.save(), capacity, STRINGS, 256, 64, 64, 4L * 1024L * 1024L);

        assertEquals(host.hostId(), restored.hostId());
        assertEquals(host.externalReservations(), restored.externalReservations());
        assertEquals(host.reserved(), restored.reserved());
        assertEquals(capacity, restored.physicalCapacity());
        assertEquals(Long.MAX_VALUE, restored.availableAsSaturatedLong());
    }

    @Test
    void currentStructureCapacityOverridesPersistedCapacityOnLoad() {
        BigCraftingHostRuntime<String> host = host(BigInteger.TEN.pow(40));
        CompoundTag saved = host.save();

        BigInteger replacement = BigInteger.TEN.pow(50);
        BigCraftingHostRuntime<String> restored = BigCraftingHostRuntime.load(
                saved, replacement, STRINGS, 256, 64, 64, 4L * 1024L * 1024L);

        assertEquals(replacement, restored.physicalCapacity());
        assertEquals(replacement, restored.available());
    }

    @Test
    void persistsNativeBigJobReservationAndIdentity() {
        BigInteger capacity = BigInteger.TEN.pow(64);
        BigCraftingHostRuntime<String> host = host(capacity);
        BigCraftingJob<String> job = job(BigInteger.TEN.pow(30));
        assertTrue(host.submit(job));

        BigCraftingHostRuntime<String> restored = BigCraftingHostRuntime.load(
                host.save(), capacity, STRINGS, 256, 64, 64, 4L * 1024L * 1024L);

        assertEquals(host.hostId(), restored.hostId());
        assertEquals(BigInteger.TEN.pow(30), restored.bigReserved());
        assertEquals(host.bigJobIds(), restored.bigJobIds());
    }

    @Test
    void malformedDuplicateReservationsFailClosed() {
        BigCraftingHostRuntime<String> host = host(BigInteger.TEN.pow(30));
        UUID jobId = UUID.randomUUID();
        assertTrue(host.reserveExternal(jobId, BigInteger.TEN));
        CompoundTag saved = host.save();
        ListTag reservations = saved.getList("externalReservations", CompoundTag.TAG_COMPOUND);
        reservations.add(reservations.getCompound(0).copy());

        assertThrows(
                IllegalArgumentException.class,
                () -> BigCraftingHostRuntime.load(
                        saved,
                        BigInteger.TEN.pow(30),
                        STRINGS,
                        256,
                        64,
                        64,
                        4L * 1024L * 1024L));
    }

    @Test
    void invalidAuthoritativeReplacementDoesNotMutateExistingReservations() {
        BigCraftingHostRuntime<String> host = host(BigInteger.valueOf(1_000));
        UUID original = UUID.randomUUID();
        assertTrue(host.reserveExternal(original, BigInteger.valueOf(400)));

        assertThrows(
                IllegalArgumentException.class,
                () -> host.replaceExternalReservations(Map.of(UUID.randomUUID(), BigInteger.ZERO)));

        assertEquals(Map.of(original, BigInteger.valueOf(400)), host.externalReservations());
        assertEquals(BigInteger.valueOf(600), host.available());
    }

    @Test
    void externalReservationMagnitudeBudgetIsBoundedAndAtomic() {
        BigCraftingHostRuntime<String> host = new BigCraftingHostRuntime<>(
                BigInteger.ONE.shiftLeft(1_000), STRINGS, 1024, 64, 64, 200L);
        BigInteger largeReservation = BigInteger.ONE.shiftLeft(900);
        UUID first = UUID.randomUUID();

        assertTrue(host.reserveExternal(first, largeReservation));
        assertFalse(host.reserveExternal(UUID.randomUUID(), largeReservation));
        assertThrows(
                IllegalArgumentException.class,
                () -> host.replaceExternalReservations(
                        Map.of(
                                UUID.randomUUID(), largeReservation,
                                UUID.randomUUID(), largeReservation)));
        assertEquals(Map.of(first, largeReservation), host.externalReservations());
        assertEquals(largeReservation.toByteArray().length, host.estimatedExternalCountBytes());
    }

    private static BigCraftingHostRuntime<String> host(BigInteger capacity) {
        return new BigCraftingHostRuntime<>(
                capacity, STRINGS, 256, 64, 64, 4L * 1024L * 1024L);
    }

    private static BigCraftingJob<String> job(BigInteger reservation) {
        return new BigCraftingJob<>(
                UUID.randomUUID(),
                "out",
                BigInteger.ONE,
                reservation,
                Map.of("pattern", BigInteger.ONE),
                Map.of());
    }
}

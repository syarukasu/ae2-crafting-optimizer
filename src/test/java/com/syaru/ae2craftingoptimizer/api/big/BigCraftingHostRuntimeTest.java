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

    @Test
    void promotedCapacityReservationSurvivesReconcileAndReload() {
        BigInteger exact = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
        BigInteger capacity = exact.add(BigInteger.valueOf(1_000L));
        UUID cpuId = UUID.randomUUID();
        BigCraftingHostRuntime<String> host = host(capacity);

        // Advanced AEは最初に互換用Long.MAX_VALUEを登録し、成功後にACOが真値へ昇格する。
        host.replaceExternalReservations(Map.of(cpuId, BigInteger.valueOf(Long.MAX_VALUE)));
        assertTrue(host.promoteExternalReservation(cpuId, exact));
        assertEquals(exact, host.externalReserved());

        // CPU本体の再走査は互換値しか返さないため、既存Sidecar真値を正本として維持する。
        host.replaceExternalReservations(Map.of(cpuId, BigInteger.valueOf(Long.MAX_VALUE)));
        assertEquals(Map.of(cpuId, exact), host.externalReservations());

        BigCraftingHostRuntime<String> restored = BigCraftingHostRuntime.load(
                host.save(), capacity, STRINGS, 256, 64, 64, 4L * 1024L * 1024L);
        assertEquals(Map.of(cpuId, exact), restored.externalReservations());
        assertEquals(exact, restored.reserved());

        // CPUが完了して正本一覧から消えた時だけ、BigInteger予約も解放する。
        restored.replaceExternalReservations(Map.of());
        assertEquals(BigInteger.ZERO, restored.reserved());
        assertEquals(capacity, restored.available());
    }

    @Test
    void failedCapacityPromotionLeavesFacadeReservationUntouched() {
        BigInteger facade = BigInteger.valueOf(Long.MAX_VALUE);
        BigInteger capacity = facade.add(BigInteger.TEN);
        BigInteger tooLarge = facade.multiply(BigInteger.TWO);
        UUID cpuId = UUID.randomUUID();
        BigCraftingHostRuntime<String> host = host(capacity);
        host.replaceExternalReservations(Map.of(cpuId, facade));

        assertFalse(host.promoteExternalReservation(cpuId, tooLarge));
        assertEquals(Map.of(cpuId, facade), host.externalReservations());
        assertEquals(facade, host.reserved());
    }

    @Test
    void capacityPromotionDoesNotAlterNativeBigJobReservation() {
        BigInteger exact = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
        BigInteger nativeReservation = BigInteger.valueOf(400L);
        BigCraftingHostRuntime<String> host = host(exact.add(BigInteger.valueOf(1_000L)));
        assertTrue(host.submit(job(nativeReservation)));
        UUID cpuId = UUID.randomUUID();
        host.replaceExternalReservations(Map.of(cpuId, BigInteger.valueOf(Long.MAX_VALUE)));

        assertTrue(host.promoteExternalReservation(cpuId, exact));
        assertEquals(exact, host.externalReserved());
        assertEquals(nativeReservation, host.bigReserved());
        assertEquals(exact.add(nativeReservation), host.reserved());
    }

    @Test
    void childExecutionBindingSurvivesReloadWithoutDoubleCountingCapacity() {
        BigCraftingHostRuntime<String> host = host(BigInteger.valueOf(1_000));
        BigCraftingJob<String> job = BigCraftingJob.rootWindowed(
                UUID.randomUUID(), "out", BigInteger.ONE, BigInteger.valueOf(500), 12L, 34L);
        assertTrue(host.submit(job));
        var lease = host.schedule(1L, 1).get(0);
        UUID childCpu = UUID.randomUUID();

        // Advanced AEのactive CPU走査が先に子Jobを予約しても、Binding後はBig側だけを数える。
        host.replaceExternalReservations(Map.of(childCpu, BigInteger.valueOf(500)));
        assertEquals(BigInteger.valueOf(1_000), host.reserved());
        host.bindExternalExecution(lease, childCpu, BigInteger.valueOf(500));
        assertEquals(BigInteger.valueOf(500), host.reserved());

        BigCraftingHostRuntime<String> restored = BigCraftingHostRuntime.load(
                host.save(), BigInteger.valueOf(1_000), STRINGS, 256, 64, 64, 4L * 1024L * 1024L);
        assertEquals(1, restored.externalExecutions().size());
        assertTrue(restored.externalReservations().isEmpty());
        assertEquals(BigInteger.valueOf(500), restored.reserved());

        assertTrue(restored.resolveExternalExecution(childCpu, true));
        assertEquals(BigInteger.ZERO, restored.reserved());
        assertTrue(restored.bigJobIds().isEmpty());
    }

    @Test
    void failedChildRollsBackExactlyOnePreparedWindow() {
        BigCraftingHostRuntime<String> host = host(BigInteger.valueOf(1_000));
        BigCraftingJob<String> job = BigCraftingJob.rootWindowed(
                UUID.randomUUID(), "out", BigInteger.valueOf(2), BigInteger.valueOf(500));
        assertTrue(host.submit(job));
        var first = host.schedule(1L, 1).get(0);
        UUID childCpu = UUID.randomUUID();
        host.bindExternalExecution(first, childCpu, BigInteger.valueOf(500));

        assertTrue(host.resolveExternalExecution(childCpu, false));
        var replacement = host.schedule(1L, 1).get(0);
        assertEquals(first.prepared().window(), replacement.prepared().window());
        assertFalse(first.prepared().transactionId().equals(replacement.prepared().transactionId()));
    }

    @Test
    void onlyUnboundPreparedWindowsAreRolledBack() {
        BigCraftingHostRuntime<String> host = host(BigInteger.valueOf(1_000));
        BigCraftingJob<String> first = BigCraftingJob.rootWindowed(
                UUID.randomUUID(), "first", BigInteger.ONE, BigInteger.valueOf(400));
        BigCraftingJob<String> second = BigCraftingJob.rootWindowed(
                UUID.randomUUID(), "second", BigInteger.ONE, BigInteger.valueOf(400));
        assertTrue(host.submit(first));
        assertTrue(host.submit(second));
        var leases = host.schedule(2L, 2);
        host.bindExternalExecution(leases.get(0), UUID.randomUUID(), BigInteger.valueOf(400));

        assertEquals(1, host.rollbackUnboundPreparedExecutions());
        assertEquals(1, host.unresolvedExecutions().size());
        assertEquals(
                leases.get(0).prepared().transactionId(),
                host.unresolvedExecutions().get(0).prepared().transactionId());
    }

    @Test
    void missingBoundChildQuarantinesInsteadOfGuessingItsOutcome() {
        BigCraftingHostRuntime<String> host = host(BigInteger.valueOf(1_000));
        BigCraftingJob<String> job = BigCraftingJob.rootWindowed(
                UUID.randomUUID(), "out", BigInteger.ONE, BigInteger.valueOf(500));
        assertTrue(host.submit(job));
        var lease = host.schedule(1L, 1).get(0);
        UUID childCpu = UUID.randomUUID();
        host.bindExternalExecution(lease, childCpu, BigInteger.valueOf(500));

        assertTrue(host.quarantineExternalExecution(childCpu));
        assertEquals(BigInteger.valueOf(500), host.reserved());
        assertEquals(
                BigCraftingJob.State.QUARANTINED,
                host.statusPage(0, 1).jobs().get(0).state());
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

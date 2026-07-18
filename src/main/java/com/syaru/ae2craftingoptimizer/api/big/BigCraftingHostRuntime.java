package com.syaru.ae2craftingoptimizer.api.big;

import com.syaru.ae2craftingoptimizer.engine.BigCountMath;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingJob;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingKeyCodec;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Shared capacity owner for one add-on crafting CPU.
 *
 * <p>Normal AE2 jobs reserve signed-long amounts through {@link #reserveExternal}, while native
 * BigInteger jobs use the embedded {@link BigCraftingRuntime}. Both paths consume the same physical
 * capacity and therefore cannot oversubscribe each other.</p>
 */
public final class BigCraftingHostRuntime<K> {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_EXTERNAL_RESERVATIONS = 65_536;
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final UUID hostId;
    private final BigCraftingKeyCodec<K> keyCodec;
    private final int maximumBits;
    private final int maximumExecutionsPerWindow;
    private final int maximumStatusPageEntries;
    private final long maximumRuntimeCountBytes;
    private final Map<UUID, BigInteger> externalReservations = new LinkedHashMap<>();
    private BigInteger physicalCapacity;
    private BigInteger externalReserved = BigInteger.ZERO;
    private long externalCountBytes;
    private final BigCraftingRuntime<K> bigRuntime;

    BigCraftingHostRuntime(
            BigInteger physicalCapacity,
            BigCraftingKeyCodec<K> keyCodec,
            int maximumBits,
            int maximumExecutionsPerWindow,
            int maximumStatusPageEntries,
            long maximumRuntimeCountBytes) {
        this(
                UUID.randomUUID(),
                checkedCapacity(physicalCapacity, maximumBits),
                Map.of(),
                new BigCraftingRuntime<>(
                        physicalCapacity,
                        keyCodec,
                        maximumBits,
                        maximumExecutionsPerWindow,
                        maximumStatusPageEntries,
                        maximumRuntimeCountBytes),
                keyCodec,
                maximumBits,
                maximumExecutionsPerWindow,
                maximumStatusPageEntries,
                maximumRuntimeCountBytes);
    }

    private BigCraftingHostRuntime(
            UUID hostId,
            BigInteger physicalCapacity,
            Map<UUID, BigInteger> externalReservations,
            BigCraftingRuntime<K> bigRuntime,
            BigCraftingKeyCodec<K> keyCodec,
            int maximumBits,
            int maximumExecutionsPerWindow,
            int maximumStatusPageEntries,
            long maximumRuntimeCountBytes) {
        this.hostId = Objects.requireNonNull(hostId, "hostId");
        this.keyCodec = Objects.requireNonNull(keyCodec, "keyCodec");
        this.maximumBits = maximumBits;
        this.maximumExecutionsPerWindow = maximumExecutionsPerWindow;
        this.maximumStatusPageEntries = maximumStatusPageEntries;
        this.maximumRuntimeCountBytes = maximumRuntimeCountBytes;
        this.physicalCapacity = checkedCapacity(physicalCapacity, maximumBits);
        this.bigRuntime = Objects.requireNonNull(bigRuntime, "bigRuntime");
        replaceExternalReservations(externalReservations);
    }

    /**
     * Atomically reserves capacity for a normal AE2/Advanced AE job.
     */
    public synchronized boolean reserveExternal(UUID jobId, BigInteger amount) {
        Objects.requireNonNull(jobId, "jobId");
        BigInteger checked = checkedPositive(amount, "external reservation", maximumBits);
        if (externalReservations.containsKey(jobId)) {
            throw new IllegalStateException("external job already has a reservation: " + jobId);
        }
        long amountBytes = BigCountMath.encodedBytes(checked);
        if (externalReservations.size() >= MAX_EXTERNAL_RESERVATIONS
                || exceedsExternalCountBudget(amountBytes)
                || available().compareTo(checked) < 0) {
            return false;
        }
        externalReservations.put(jobId, checked);
        externalReserved = BigCountMath.add(
                externalReserved, checked, "host/externalReserved", maximumBits);
        externalCountBytes = Math.addExact(externalCountBytes, amountBytes);
        rebalanceBigRuntimeCapacity();
        return true;
    }

    public synchronized BigInteger releaseExternal(UUID jobId) {
        BigInteger released = externalReservations.remove(Objects.requireNonNull(jobId, "jobId"));
        if (released == null) {
            return BigInteger.ZERO;
        }
        externalReserved = externalReserved.subtract(released);
        externalCountBytes = Math.subtractExact(
                externalCountBytes, BigCountMath.encodedBytes(released));
        rebalanceBigRuntimeCapacity();
        return released;
    }

    /**
     * Replaces normal-job reservations from the authoritative CPU job map.
     *
     * <p>This method deliberately accepts an overcommitted restored state. Existing work is kept,
     * available capacity becomes zero, and no new work can be accepted until capacity is restored
     * or jobs finish.</p>
     */
    public synchronized void replaceExternalReservations(Map<UUID, BigInteger> replacement) {
        CheckedReservations checked = checkedReservations(
                replacement, maximumBits, maximumRuntimeCountBytes);
        BigInteger total = BigInteger.ZERO;
        for (BigInteger amount : checked.values().values()) {
            total = BigCountMath.add(total, amount, "host/externalReserved", maximumBits);
        }
        externalReservations.clear();
        externalReservations.putAll(checked.values());
        externalReserved = total;
        externalCountBytes = checked.encodedCountBytes();
        rebalanceBigRuntimeCapacity();
    }

    public synchronized void resizePhysicalCapacity(BigInteger replacement) {
        physicalCapacity = checkedCapacity(replacement, maximumBits);
        rebalanceBigRuntimeCapacity();
    }

    public synchronized boolean submit(BigCraftingJob<K> job) {
        Objects.requireNonNull(job, "job");
        if (available().compareTo(job.reservedCapacity()) < 0) {
            return false;
        }
        rebalanceBigRuntimeCapacity();
        return bigRuntime.submit(job);
    }

    public synchronized List<BigCraftingRuntime.ExecutionLease<K>> schedule(long operationBudget) {
        return bigRuntime.schedule(operationBudget);
    }

    public synchronized void commit(
            BigCraftingRuntime.ExecutionLease<K> lease,
            long acceptedExecutions,
            Map<K, BigInteger> expectedOutputs) {
        bigRuntime.commit(lease, acceptedExecutions, expectedOutputs);
        rebalanceBigRuntimeCapacity();
    }

    public synchronized void rollback(BigCraftingRuntime.ExecutionLease<K> lease) {
        bigRuntime.rollback(lease);
    }

    public synchronized void acceptOutput(UUID jobId, K key, BigInteger amount) {
        bigRuntime.acceptOutput(jobId, key, amount);
        rebalanceBigRuntimeCapacity();
    }

    public synchronized boolean cancel(UUID jobId) {
        boolean cancelled = bigRuntime.cancel(jobId);
        if (cancelled) {
            rebalanceBigRuntimeCapacity();
        }
        return cancelled;
    }

    public synchronized List<BigCraftingRuntime.RecoveredExecution<K>> unresolvedExecutions() {
        return bigRuntime.unresolvedExecutions();
    }

    public synchronized void commitRecovered(
            UUID jobId,
            UUID transactionId,
            long acceptedExecutions,
            Map<K, BigInteger> expectedOutputs) {
        bigRuntime.commitRecovered(jobId, transactionId, acceptedExecutions, expectedOutputs);
        rebalanceBigRuntimeCapacity();
    }

    public synchronized void rollbackRecovered(UUID jobId, UUID transactionId) {
        bigRuntime.rollbackRecovered(jobId, transactionId);
    }

    public synchronized BigCraftingStatusPage<K> statusPage(int offset, int requestedPageSize) {
        return bigRuntime.statusPage(offset, requestedPageSize);
    }

    public synchronized CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA_VERSION);
        tag.putUUID("hostId", hostId);
        putNonNegative(tag, "physicalCapacity", physicalCapacity, maximumBits);
        ListTag external = new ListTag();
        for (var entry : externalReservations.entrySet()) {
            CompoundTag reservation = new CompoundTag();
            reservation.putUUID("jobId", entry.getKey());
            putNonNegative(reservation, "amount", entry.getValue(), maximumBits);
            external.add(reservation);
        }
        tag.put("externalReservations", external);
        tag.put("bigRuntime", bigRuntime.save());
        return tag;
    }

    static <K> BigCraftingHostRuntime<K> load(
            CompoundTag tag,
            BigInteger currentPhysicalCapacity,
            BigCraftingKeyCodec<K> keyCodec,
            int maximumBits,
            int maximumExecutionsPerWindow,
            int maximumStatusPageEntries,
            long maximumRuntimeCountBytes) {
        Objects.requireNonNull(tag, "tag");
        if (tag.getInt("schema") != SCHEMA_VERSION
                || !tag.hasUUID("hostId")
                || !tag.contains("bigRuntime", Tag.TAG_COMPOUND)
                || !tag.contains("externalReservations", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("unsupported BigInteger host runtime schema");
        }
        // Validate the persisted value even though current structure/config is authoritative.
        readNonNegative(tag, "physicalCapacity", maximumBits);
        Map<UUID, BigInteger> external = readReservations(tag, maximumBits);
        BigCraftingRuntime<K> runtime = BigCraftingRuntime.load(
                tag.getCompound("bigRuntime"),
                keyCodec,
                maximumBits,
                maximumExecutionsPerWindow,
                maximumStatusPageEntries,
                maximumRuntimeCountBytes);
        return new BigCraftingHostRuntime<>(
                tag.getUUID("hostId"),
                currentPhysicalCapacity,
                external,
                runtime,
                keyCodec,
                maximumBits,
                maximumExecutionsPerWindow,
                maximumStatusPageEntries,
                maximumRuntimeCountBytes);
    }

    public UUID hostId() {
        return hostId;
    }

    public synchronized BigInteger physicalCapacity() {
        return physicalCapacity;
    }

    public synchronized BigInteger externalReserved() {
        return externalReserved;
    }

    public synchronized BigInteger bigReserved() {
        return bigRuntime.reserved();
    }

    public synchronized BigInteger reserved() {
        return BigCountMath.add(
                externalReserved, bigRuntime.reserved(), "host/reserved", maximumBits);
    }

    public synchronized BigInteger available() {
        BigInteger remaining = physicalCapacity.subtract(reserved());
        return remaining.signum() < 0 ? BigInteger.ZERO : remaining;
    }

    public synchronized boolean isOvercommitted() {
        return reserved().compareTo(physicalCapacity) > 0;
    }

    public synchronized long physicalCapacityAsSaturatedLong() {
        return saturatedLong(physicalCapacity);
    }

    public synchronized long availableAsSaturatedLong() {
        return saturatedLong(available());
    }

    public synchronized Map<UUID, BigInteger> externalReservations() {
        return Map.copyOf(externalReservations);
    }

    public synchronized long estimatedExternalCountBytes() {
        return externalCountBytes;
    }

    public synchronized List<UUID> bigJobIds() {
        return bigRuntime.jobIds();
    }

    public int maximumBits() {
        return maximumBits;
    }

    private void rebalanceBigRuntimeCapacity() {
        BigInteger physicalForBigJobs = physicalCapacity.subtract(externalReserved);
        if (physicalForBigJobs.signum() < 0) {
            physicalForBigJobs = BigInteger.ZERO;
        }
        BigInteger replacement = physicalForBigJobs.max(bigRuntime.reserved());
        if (!bigRuntime.resizeCapacity(replacement)) {
            throw new IllegalStateException("failed to reconcile BigInteger host capacity");
        }
    }

    private static CheckedReservations checkedReservations(
            Map<UUID, BigInteger> source,
            int maximumBits,
            long maximumCountBytes) {
        Objects.requireNonNull(source, "external reservations");
        if (source.size() > MAX_EXTERNAL_RESERVATIONS) {
            throw new IllegalArgumentException("too many external crafting reservations");
        }
        Map<UUID, BigInteger> checked = new LinkedHashMap<>();
        long countBytes = 0L;
        source.forEach((jobId, amount) -> {
            UUID id = Objects.requireNonNull(jobId, "external job id");
            BigInteger value = checkedPositive(amount, "external reservation", maximumBits);
            if (checked.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException("duplicate external crafting reservation " + id);
            }
        });
        for (BigInteger value : checked.values()) {
            countBytes = Math.addExact(countBytes, BigCountMath.encodedBytes(value));
            if (countBytes > maximumCountBytes) {
                throw new IllegalArgumentException(
                        "external crafting reservation count budget exceeded");
            }
        }
        return new CheckedReservations(Map.copyOf(checked), countBytes);
    }

    private static Map<UUID, BigInteger> readReservations(CompoundTag owner, int maximumBits) {
        Tag raw = owner.get("externalReservations");
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
                || list.size() > MAX_EXTERNAL_RESERVATIONS) {
            throw new IllegalArgumentException("malformed or oversized external reservation list");
        }
        Map<UUID, BigInteger> result = new LinkedHashMap<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (!entry.hasUUID("jobId")) {
                throw new IllegalArgumentException("external reservation is missing its job id");
            }
            UUID id = entry.getUUID("jobId");
            BigInteger amount = checkedPositive(
                    readNonNegative(entry, "amount", maximumBits),
                    "external reservation",
                    maximumBits);
            if (result.putIfAbsent(id, amount) != null) {
                throw new IllegalArgumentException("duplicate external reservation " + id);
            }
        }
        return result;
    }

    private static BigInteger checkedCapacity(BigInteger value, int maximumBits) {
        return BigCountMath.requireMaximumBits(
                BigCountMath.requireNonNegative(value, "physical capacity"),
                "physical capacity",
                maximumBits);
    }

    private static BigInteger checkedPositive(BigInteger value, String name, int maximumBits) {
        BigInteger checked = BigCountMath.requireMaximumBits(
                BigCountMath.requireNonNegative(value, name), name, maximumBits);
        if (checked.signum() == 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return checked;
    }

    private static void putNonNegative(
            CompoundTag owner,
            String key,
            BigInteger value,
            int maximumBits) {
        BigInteger checked = BigCountMath.requireMaximumBits(
                BigCountMath.requireNonNegative(value, key), key, maximumBits);
        owner.putByteArray(key, checked.toByteArray());
    }

    private static BigInteger readNonNegative(CompoundTag owner, String key, int maximumBits) {
        if (!owner.contains(key, Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalArgumentException("missing BigInteger byte array " + key);
        }
        byte[] encoded = owner.getByteArray(key);
        int maximumBytes = Math.addExact(maximumBits, 8) / 8;
        if (encoded.length == 0 || encoded.length > maximumBytes) {
            throw new IllegalArgumentException("invalid or oversized BigInteger byte array " + key);
        }
        BigInteger value = new BigInteger(encoded);
        BigInteger checked = BigCountMath.requireMaximumBits(
                BigCountMath.requireNonNegative(value, key), key, maximumBits);
        if (!java.util.Arrays.equals(encoded, checked.toByteArray())) {
            throw new IllegalArgumentException("non-canonical BigInteger byte array " + key);
        }
        return checked;
    }

    private static long saturatedLong(BigInteger value) {
        return value.compareTo(LONG_MAX) >= 0 ? Long.MAX_VALUE : value.longValueExact();
    }

    private boolean exceedsExternalCountBudget(long additionalBytes) {
        if (additionalBytes < 0L || externalCountBytes > maximumRuntimeCountBytes - additionalBytes) {
            return true;
        }
        return false;
    }

    private record CheckedReservations(
            Map<UUID, BigInteger> values,
            long encodedCountBytes) {
    }
}

package com.syaru.ae2craftingoptimizer.api.big;

import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;

/** Immutable, atomic accounting view of one Big Crafting Host. */
public final class BigCraftingHostSnapshot {
    private final UUID runtimeId;
    private final long revision;
    private final BigInteger physicalCapacity;
    private final BigInteger reserved;
    private final BigInteger available;
    private final BigInteger externalReserved;
    private final BigInteger bigReserved;
    private final long standardJobCount;
    private final long bigJobCount;
    private final long managedChildJobCount;
    private final boolean overcommitted;
    private final BigCraftingHostBackendState backendState;

    private BigCraftingHostSnapshot(
            UUID runtimeId,
            long revision,
            BigInteger physicalCapacity,
            BigInteger reserved,
            BigInteger available,
            BigInteger externalReserved,
            BigInteger bigReserved,
            long standardJobCount,
            long bigJobCount,
            long managedChildJobCount,
            boolean overcommitted,
            BigCraftingHostBackendState backendState) {
        this.runtimeId = runtimeId;
        this.revision = revision;
        this.physicalCapacity = physicalCapacity;
        this.reserved = reserved;
        this.available = available;
        this.externalReserved = externalReserved;
        this.bigReserved = bigReserved;
        this.standardJobCount = standardJobCount;
        this.bigJobCount = bigJobCount;
        this.managedChildJobCount = managedChildJobCount;
        this.overcommitted = overcommitted;
        this.backendState = backendState;
    }

    /** Derives available from one accounting revision and clamps it on overcommit. */
    public static BigCraftingHostSnapshot of(
            UUID runtimeId,
            long revision,
            BigInteger physicalCapacity,
            BigInteger reserved,
            BigInteger externalReserved,
            BigInteger bigReserved,
            long standardJobCount,
            long bigJobCount,
            long managedChildJobCount,
            BigCraftingHostBackendState backendState) {
        Objects.requireNonNull(runtimeId, "runtimeId");
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        requireNonNegative(physicalCapacity, "physicalCapacity");
        requireNonNegative(reserved, "reserved");
        requireNonNegative(externalReserved, "externalReserved");
        requireNonNegative(bigReserved, "bigReserved");
        if (externalReserved.add(bigReserved).compareTo(reserved) > 0) {
            throw new IllegalArgumentException(
                    "external and BigInteger reservations exceed total reservation");
        }
        requireNonNegative(standardJobCount, "standardJobCount");
        requireNonNegative(bigJobCount, "bigJobCount");
        requireNonNegative(managedChildJobCount, "managedChildJobCount");
        Objects.requireNonNull(backendState, "backendState");

        boolean overcommitted = reserved.compareTo(physicalCapacity) > 0;
        BigInteger available = overcommitted
                ? BigInteger.ZERO
                : physicalCapacity.subtract(reserved);
        return new BigCraftingHostSnapshot(
                runtimeId,
                revision,
                physicalCapacity,
                reserved,
                available,
                externalReserved,
                bigReserved,
                standardJobCount,
                bigJobCount,
                managedChildJobCount,
                overcommitted,
                backendState);
    }

    public UUID runtimeId() { return runtimeId; }
    public long revision() { return revision; }
    public BigInteger physicalCapacity() { return physicalCapacity; }
    public BigInteger reserved() { return reserved; }
    public BigInteger available() { return available; }
    public BigInteger externalReserved() { return externalReserved; }
    public BigInteger bigReserved() { return bigReserved; }
    public long standardJobCount() { return standardJobCount; }
    public long bigJobCount() { return bigJobCount; }
    public long managedChildJobCount() { return managedChildJobCount; }
    public boolean overcommitted() { return overcommitted; }
    public BigCraftingHostBackendState backendState() { return backendState; }

    private static void requireNonNegative(BigInteger value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}

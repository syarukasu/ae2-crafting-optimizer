package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class BigCraftingCapacityLedger {
    private BigInteger capacity;
    private final Map<UUID, BigInteger> reservations = new LinkedHashMap<>();
    private BigInteger reserved = BigInteger.ZERO;

    public BigCraftingCapacityLedger(BigInteger capacity) {
        this.capacity = BigCountMath.requireNonNegative(capacity, "capacity");
    }

    public synchronized boolean reserve(UUID jobId, BigInteger amount) {
        Objects.requireNonNull(jobId, "jobId");
        BigCountMath.requireNonNegative(amount, "reservation");
        if (reservations.containsKey(jobId)) {
            throw new IllegalStateException("job already has a capacity reservation: " + jobId);
        }
        if (reserved.add(amount).compareTo(capacity) > 0) {
            return false;
        }
        reservations.put(jobId, amount);
        reserved = reserved.add(amount);
        return true;
    }

    public synchronized BigInteger release(UUID jobId) {
        BigInteger amount = reservations.remove(Objects.requireNonNull(jobId, "jobId"));
        if (amount == null) {
            return BigInteger.ZERO;
        }
        reserved = reserved.subtract(amount);
        return amount;
    }

    public synchronized boolean resize(BigInteger replacement) {
        BigCountMath.requireNonNegative(replacement, "replacement capacity");
        if (replacement.compareTo(reserved) < 0) {
            return false;
        }
        capacity = replacement;
        return true;
    }

    public synchronized BigInteger capacity() {
        return capacity;
    }

    public synchronized BigInteger reserved() {
        return reserved;
    }

    public synchronized BigInteger available() {
        return capacity.subtract(reserved);
    }

    public synchronized Map<UUID, BigInteger> snapshot() {
        return Map.copyOf(reservations);
    }

    public synchronized void restore(Map<UUID, BigInteger> restored) {
        Objects.requireNonNull(restored, "restored");
        Map<UUID, BigInteger> checked = new LinkedHashMap<>();
        BigInteger total = BigInteger.ZERO;
        for (Map.Entry<UUID, BigInteger> entry : restored.entrySet()) {
            UUID id = Objects.requireNonNull(entry.getKey(), "restored job id");
            BigInteger amount = BigCountMath.requireNonNegative(entry.getValue(), "restored reservation");
            total = total.add(amount);
            if (total.compareTo(capacity) > 0) {
                throw new IllegalArgumentException("restored reservations exceed crafting capacity");
            }
            checked.put(id, amount);
        }
        reservations.clear();
        reservations.putAll(checked);
        reserved = total;
    }
}

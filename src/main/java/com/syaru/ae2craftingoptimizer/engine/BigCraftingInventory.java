package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** BigInteger accounting inventory. It never exposes truncating long conversions. */
public final class BigCraftingInventory<K> {
    private final Map<K, BigInteger> amounts = new LinkedHashMap<>();
    private BigInteger totalAmount = BigInteger.ZERO;
    private long encodedCountBytes;
    private Transaction<K> activeTransaction;

    public BigCraftingInventory() {
    }

    public BigCraftingInventory(Map<K, BigInteger> initial) {
        restore(initial);
    }

    public synchronized BigInteger amount(K key) {
        return amounts.getOrDefault(Objects.requireNonNull(key, "key"), BigInteger.ZERO);
    }

    public synchronized void insert(K key, BigInteger amount) {
        ensureNoActiveTransaction();
        Objects.requireNonNull(key, "key");
        BigCountMath.requireNonNegative(amount, "inventory/insert");
        if (amount.signum() != 0) {
            BigInteger current = amounts.get(key);
            BigInteger next = current == null ? amount : current.add(amount);
            amounts.put(key, next);
            totalAmount = totalAmount.add(amount);
            encodedCountBytes = replaceEncodedBytes(encodedCountBytes, current, next);
        }
    }

    public synchronized boolean canExtract(K key, BigInteger amount) {
        BigCountMath.requireNonNegative(amount, "inventory/simulateExtract");
        return amount(key).compareTo(amount) >= 0;
    }

    public synchronized void extractExact(K key, BigInteger amount) {
        ensureNoActiveTransaction();
        Objects.requireNonNull(key, "key");
        BigCountMath.requireNonNegative(amount, "inventory/extract");
        if (amount.signum() == 0) {
            return;
        }
        BigInteger current = amount(key);
        if (current.compareTo(amount) < 0) {
            throw new IllegalStateException("insufficient BigInteger crafting inventory for " + key);
        }
        BigInteger remaining = current.subtract(amount);
        if (remaining.signum() == 0) {
            amounts.remove(key);
        } else {
            amounts.put(key, remaining);
        }
        totalAmount = totalAmount.subtract(amount);
        encodedCountBytes = replaceEncodedBytes(
                encodedCountBytes, current, remaining.signum() == 0 ? null : remaining);
    }

    public synchronized Map<K, BigInteger> snapshot() {
        return Map.copyOf(amounts);
    }

    public synchronized BigInteger totalAmount() {
        return totalAmount;
    }

    public synchronized int distinctKeys() {
        return amounts.size();
    }

    public synchronized long encodedCountBytes() {
        return encodedCountBytes;
    }

    public synchronized long projectedEncodedCountBytesAfterInserts(Map<K, BigInteger> inserts) {
        Objects.requireNonNull(inserts, "inserts");
        long projected = encodedCountBytes;
        for (var entry : inserts.entrySet()) {
            K key = Objects.requireNonNull(entry.getKey(), "insert key");
            BigInteger amount = BigCountMath.requireNonNegative(entry.getValue(), "projected insert");
            if (amount.signum() == 0) {
                continue;
            }
            BigInteger current = amounts.get(key);
            BigInteger next = current == null ? amount : current.add(amount);
            projected = replaceEncodedBytes(projected, current, next);
        }
        return projected;
    }

    public synchronized BigInteger projectedAmountAfterInsert(K key, BigInteger insert) {
        Objects.requireNonNull(key, "key");
        BigCountMath.requireNonNegative(insert, "projected insert");
        return amounts.getOrDefault(key, BigInteger.ZERO).add(insert);
    }

    public synchronized BigInteger projectedTotalAmountAfterInserts(Map<K, BigInteger> inserts) {
        Objects.requireNonNull(inserts, "inserts");
        BigInteger projected = totalAmount;
        for (var entry : inserts.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "insert key");
            projected = projected.add(BigCountMath.requireNonNegative(
                    entry.getValue(), "projected insert"));
        }
        return projected;
    }

    public synchronized void restore(Map<K, BigInteger> restored) {
        ensureNoActiveTransaction();
        restoreUnchecked(restored);
    }

    private void restoreUnchecked(Map<K, BigInteger> restored) {
        Objects.requireNonNull(restored, "restored");
        Map<K, BigInteger> checked = new LinkedHashMap<>();
        BigInteger checkedTotal = BigInteger.ZERO;
        long checkedBytes = 0L;
        restored.forEach((key, amount) -> {
            Objects.requireNonNull(key, "inventory key");
            BigCountMath.requireNonNegative(amount, "inventory/restore");
            if (amount.signum() != 0) {
                checked.put(key, amount);
            }
        });
        for (BigInteger amount : checked.values()) {
            checkedTotal = checkedTotal.add(amount);
            checkedBytes = Math.addExact(checkedBytes, BigCountMath.encodedBytes(amount));
        }
        amounts.clear();
        amounts.putAll(checked);
        totalAmount = checkedTotal;
        encodedCountBytes = checkedBytes;
    }

    public synchronized Transaction<K> beginTransaction() {
        if (activeTransaction != null) {
            throw new IllegalStateException("BigInteger inventory already has an active transaction");
        }
        Transaction<K> transaction = new Transaction<>(
                this, new LinkedHashMap<>(amounts), totalAmount, encodedCountBytes);
        activeTransaction = transaction;
        return transaction;
    }

    private synchronized void finishTransaction(
            Transaction<K> transaction,
            Map<K, BigInteger> committedState,
            BigInteger committedTotal,
            long committedEncodedBytes) {
        if (activeTransaction != transaction) {
            throw new IllegalStateException("BigInteger inventory transaction is not the active transaction");
        }
        if (committedState != null) {
            amounts.clear();
            amounts.putAll(committedState);
            totalAmount = Objects.requireNonNull(committedTotal, "committedTotal");
            encodedCountBytes = committedEncodedBytes;
        }
        activeTransaction = null;
    }

    private void ensureNoActiveTransaction() {
        if (activeTransaction != null) {
            throw new IllegalStateException("BigInteger inventory is locked by an active transaction");
        }
    }

    public static final class Transaction<K> implements AutoCloseable {
        private final BigCraftingInventory<K> owner;
        private final Map<K, BigInteger> working;
        private BigInteger workingTotal;
        private long workingEncodedBytes;
        private boolean closed;

        private Transaction(
                BigCraftingInventory<K> owner,
                Map<K, BigInteger> working,
                BigInteger workingTotal,
                long workingEncodedBytes) {
            this.owner = owner;
            this.working = working;
            this.workingTotal = workingTotal;
            this.workingEncodedBytes = workingEncodedBytes;
        }

        public synchronized void insert(K key, BigInteger amount) {
            ensureOpen();
            Objects.requireNonNull(key, "key");
            BigCountMath.requireNonNegative(amount, "inventory/transaction/insert");
            if (amount.signum() != 0) {
                BigInteger current = working.get(key);
                BigInteger next = current == null ? amount : current.add(amount);
                working.put(key, next);
                workingTotal = workingTotal.add(amount);
                workingEncodedBytes = replaceEncodedBytes(workingEncodedBytes, current, next);
            }
        }

        public synchronized void extractExact(K key, BigInteger amount) {
            ensureOpen();
            Objects.requireNonNull(key, "key");
            BigCountMath.requireNonNegative(amount, "inventory/transaction/extract");
            if (amount.signum() == 0) {
                return;
            }
            BigInteger current = working.getOrDefault(key, BigInteger.ZERO);
            if (current.compareTo(amount) < 0) {
                throw new IllegalStateException("insufficient BigInteger crafting inventory for " + key);
            }
            BigInteger remaining = current.subtract(amount);
            if (remaining.signum() == 0) {
                working.remove(key);
            } else {
                working.put(key, remaining);
            }
            workingTotal = workingTotal.subtract(amount);
            workingEncodedBytes = replaceEncodedBytes(
                    workingEncodedBytes, current, remaining.signum() == 0 ? null : remaining);
        }

        public synchronized void commit() {
            ensureOpen();
            owner.finishTransaction(
                    this, new LinkedHashMap<>(working), workingTotal, workingEncodedBytes);
            closed = true;
        }

        public synchronized void rollback() {
            ensureOpen();
            owner.finishTransaction(this, null, null, 0L);
            closed = true;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                owner.finishTransaction(this, null, null, 0L);
                closed = true;
            }
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("BigInteger inventory transaction is closed");
            }
        }
    }

    private static long replaceEncodedBytes(
            long currentTotal,
            BigInteger previous,
            BigInteger replacement) {
        long result = currentTotal;
        if (previous != null) {
            result = Math.subtractExact(result, BigCountMath.encodedBytes(previous));
        }
        if (replacement != null) {
            result = Math.addExact(result, BigCountMath.encodedBytes(replacement));
        }
        return result;
    }
}

package com.syaru.ae2craftingoptimizer.transaction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Native Batch一件の保存則を検証する副作用のない状態機械。
 * Item・Fluid・Chemicalは同じKey空間で別々に集計でき、完全一致しない部分受理を禁止する。
 */
public final class BatchConservationLedger<K> {
    private final UUID transactionId;
    private final long executions;
    private final Map<K, Long> expectedInputs;
    private final Map<K, Long> expectedOutputs;
    private Phase phase = Phase.PREPARED;

    public BatchConservationLedger(
            UUID transactionId,
            long executions,
            Map<K, Long> expectedInputs,
            Map<K, Long> expectedOutputs) {
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        if (executions <= 0L) {
            throw new IllegalArgumentException("executions must be positive");
        }
        this.executions = executions;
        this.expectedInputs = positiveCopy(expectedInputs, "expectedInputs");
        this.expectedOutputs = positiveCopy(expectedOutputs, "expectedOutputs");
        if (this.expectedInputs.isEmpty() || this.expectedOutputs.isEmpty()) {
            throw new IllegalArgumentException("batch conservation sets must not be empty");
        }
    }

    public synchronized void sourceExtracted(Map<K, Long> actualInputs) {
        requirePhase(Phase.PREPARED);
        requireExact(expectedInputs, actualInputs, "source extraction");
        phase = Phase.SOURCE_OWNED;
    }

    public synchronized void targetAccepted(
            UUID proofTransactionId,
            long acceptedExecutions,
            Map<K, Long> targetOwnedInputs) {
        requirePhase(Phase.SOURCE_OWNED);
        if (!transactionId.equals(proofTransactionId)
                || acceptedExecutions != executions) {
            throw new IllegalStateException("target ownership proof does not match the prepared batch");
        }
        requireExact(expectedInputs, targetOwnedInputs, "target ownership");
        phase = Phase.TARGET_OWNED;
    }

    public synchronized void sourceAccounted(long accountedExecutions) {
        requirePhase(Phase.TARGET_OWNED);
        if (accountedExecutions != executions) {
            throw new IllegalStateException("source accounting count does not match accepted executions");
        }
        phase = Phase.ACCOUNTED;
    }

    public synchronized void outputsObserved(Map<K, Long> actualOutputs) {
        requirePhase(Phase.ACCOUNTED);
        requireExact(expectedOutputs, actualOutputs, "produced outputs");
        phase = Phase.COMPLETE;
    }

    public synchronized void rolledBack(Map<K, Long> restoredInputs) {
        if (phase != Phase.PREPARED && phase != Phase.SOURCE_OWNED) {
            throw new IllegalStateException("cannot roll back after target ownership");
        }
        if (phase == Phase.SOURCE_OWNED) {
            requireExact(expectedInputs, restoredInputs, "source rollback");
        } else if (!restoredInputs.isEmpty()) {
            throw new IllegalStateException("unextracted transaction cannot restore inputs");
        }
        phase = Phase.ROLLED_BACK;
    }

    public synchronized void quarantine() {
        if (phase == Phase.COMPLETE || phase == Phase.ROLLED_BACK) {
            throw new IllegalStateException("terminal transaction cannot be quarantined");
        }
        phase = Phase.QUARANTINED;
    }

    public UUID transactionId() {
        return transactionId;
    }

    public long executions() {
        return executions;
    }

    public synchronized Phase phase() {
        return phase;
    }

    public Map<K, Long> expectedInputs() {
        return expectedInputs;
    }

    public Map<K, Long> expectedOutputs() {
        return expectedOutputs;
    }

    private void requirePhase(Phase expected) {
        if (phase != expected) {
            throw new IllegalStateException("expected batch phase " + expected + " but was " + phase);
        }
    }

    private static <K> void requireExact(Map<K, Long> expected, Map<K, Long> actual, String operation) {
        Map<K, Long> checked = positiveCopy(actual, operation);
        if (!expected.equals(checked)) {
            throw new IllegalStateException(operation + " violates the batch conservation ledger");
        }
    }

    private static <K> Map<K, Long> positiveCopy(Map<K, Long> source, String name) {
        Objects.requireNonNull(source, name);
        Map<K, Long> copy = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            Objects.requireNonNull(key, name + " key");
            if (amount == null || amount <= 0L || copy.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException(name + " must contain unique positive amounts");
            }
        });
        return Map.copyOf(copy);
    }

    public enum Phase {
        PREPARED,
        SOURCE_OWNED,
        TARGET_OWNED,
        ACCOUNTED,
        COMPLETE,
        ROLLED_BACK,
        QUARANTINED
    }
}

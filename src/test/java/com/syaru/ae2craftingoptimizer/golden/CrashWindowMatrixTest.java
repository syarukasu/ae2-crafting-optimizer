package com.syaru.ae2craftingoptimizer.golden;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class CrashWindowMatrixTest {
    @Test
    void everyOwnershipBoundaryIsConservedAndReceiptRetryIsIdempotent() {
        for (CrashWindow window : CrashWindow.values()) {
            SimulatedJournal journal = new SimulatedJournal();
            BigInteger start = BigInteger.valueOf(37L);
            UUID receipt = UUID.randomUUID();
            SimulatedJournal.Pending pending = journal.prepare(receipt, start);
            if (window.ordinal() % 2 == 0) {
                journal.accept(pending);
            }
            if (window.ordinal() % 3 == 0) {
                journal.account(receipt);
            }
            journal.retryReceipt(receipt);
            assertEquals(start, journal.total(), "conservation failed at " + window);
        }
    }

    private enum CrashWindow {
        PARENT_JOURNAL_BEFORE,
        PARENT_JOURNAL_AFTER,
        ME_EXTRACT_BEFORE,
        ME_EXTRACT_AFTER,
        AAC_THREAD_BEFORE,
        AAC_THREAD_AFTER,
        OUTPUT_READY_BEFORE,
        OUTPUT_READY_AFTER,
        TERMINAL_RECEIPT_BEFORE,
        TERMINAL_RECEIPT_AFTER,
        THREAD_RELEASE_BEFORE,
        THREAD_RELEASE_AFTER,
        ESCROW_CREDIT_BEFORE,
        ESCROW_CREDIT_AFTER,
        WORKER_FORGET_BEFORE,
        WORKER_FORGET_AFTER,
        FINAL_ME_INSERT_BEFORE,
        FINAL_ME_INSERT_AFTER,
        PARENT_CPU_COMMIT_BEFORE,
        PARENT_CPU_COMMIT_AFTER,
        CAPACITY_RELEASE_BEFORE,
        CAPACITY_RELEASE_AFTER
    }

    private static final class SimulatedJournal {
        private final Set<UUID> accepted = new java.util.HashSet<>();
        private final Set<UUID> accounted = new HashSet<>();
        private final Map<UUID, BigInteger> amounts = new HashMap<>();
        private BigInteger me = BigInteger.ZERO;
        private BigInteger escrow = BigInteger.ZERO;
        private BigInteger worker = BigInteger.ZERO;
        private BigInteger output = BigInteger.ZERO;

        private Pending prepare(UUID id, BigInteger amount) {
            amounts.put(id, amount);
            me = me.add(amount);
            return new Pending(id, amount);
        }

        private void accept(Pending pending) {
            if (accepted.add(pending.id())) {
                me = me.subtract(pending.amount());
                escrow = escrow.add(pending.amount());
            }
        }

        private void account(UUID id) {
            if (accounted.add(id)) {
                BigInteger amount = amounts.get(id);
                if (amount != null && escrow.compareTo(amount) >= 0) {
                    escrow = escrow.subtract(amount);
                    worker = worker.add(amount);
                }
            }
        }

        private void retryReceipt(UUID id) {
            // Duplicate receipt processing is intentionally a no-op.
            if (accounted.contains(id)) {
                return;
            }
            if (accepted.contains(id)) {
                account(id);
            }
        }

        private BigInteger total() {
            return me.add(escrow).add(worker).add(output);
        }

        private record Pending(UUID id, BigInteger amount) {
        }
    }
}

package com.syaru.ae2craftingoptimizer.api.vector;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExactVectorDiagnosticsTest {
    @BeforeEach
    void resetBeforeTest() {
        OptimizationMetrics.reset();
    }

    @AfterEach
    void resetAfterTest() {
        OptimizationMetrics.reset();
    }

    @Test
    void reportsQuantityIndependentLifecycleCounters() {
        ExactVectorDiagnostics.planPrepared();
        ExactVectorDiagnostics.executorRejected();
        ExactVectorDiagnostics.transactionStarted(
                VectorResourceMode.HOST_ESCROWED);
        ExactVectorDiagnostics.transactionStarted(
                VectorResourceMode.NETWORK_STORAGE);
        ExactVectorDiagnostics.activeTick(2_500L);
        ExactVectorDiagnostics.transactionCompleted();
        ExactVectorDiagnostics.transactionCancelled();
        ExactVectorDiagnostics.transactionQuarantined();
        ExactVectorDiagnostics.startBudgetDeferred();
        ExactVectorDiagnostics.receiptFreeRollback();
        ExactVectorDiagnostics.fingerprintRevalidated();
        ExactVectorDiagnostics.queueWork(7L, 2L);
        ExactVectorDiagnostics.accountingSnapshotRebuilt();
        ExactVectorDiagnostics.dirtyCallAvoided();
        ExactVectorDiagnostics.zeroAllocationWait();

        String exactVector = OptimizationMetrics.summaryLines().stream()
                .filter(line -> line.startsWith("Physical crafting tree:"))
                .findFirst()
                .orElseThrow();

        assertTrue(exactVector.contains("starts host/network 1/1"));
        assertTrue(exactVector.contains("prepared/rejected 1/1"));
        assertTrue(exactVector.contains("active scheduler tick(s) 1"));
        assertTrue(exactVector.contains("completed/cancelled/quarantined 1/1/1"));
        assertTrue(exactVector.contains(
                "start defer/receipt rollback/revalidated 1/1/1"));
        assertTrue(exactVector.contains("queue scanned/processed 7/2"));
        assertTrue(exactVector.contains("accounting rebuilds 1"));
        assertTrue(exactVector.contains("dirty calls avoided 1"));
        assertTrue(exactVector.contains("zero-allocation waits 1"));
        assertTrue(exactVector.contains("max active range 2 us"));
    }
}

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
        ExactVectorDiagnostics.energyPaused();
        ExactVectorDiagnostics.startBudgetDeferred();
        ExactVectorDiagnostics.receiptFreeRollback();
        ExactVectorDiagnostics.fingerprintRevalidated();

        String exactVector = OptimizationMetrics.summaryLines().stream()
                .filter(line -> line.startsWith("Exact Vector:"))
                .findFirst()
                .orElseThrow();

        assertTrue(exactVector.contains("starts host/network 1/1"));
        assertTrue(exactVector.contains("prepared/rejected 1/1"));
        assertTrue(exactVector.contains("logical stage(s) 1"));
        assertTrue(exactVector.contains("completed/cancelled/quarantined 1/1/1"));
        assertTrue(exactVector.contains("energy pause(s) 1"));
        assertTrue(exactVector.contains(
                "start defer/receipt rollback/revalidated 1/1/1"));
        assertTrue(exactVector.contains("max active range 2 us"));
    }
}
